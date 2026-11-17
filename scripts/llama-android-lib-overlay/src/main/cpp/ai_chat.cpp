#include <android/log.h>
#include <jni.h>
#include <atomic>
#include <iomanip>
#include <cmath>
#include <string>
#include <unistd.h>
#include <vector>
#include <sstream>
#include <sampling.h>

#include "logging.h"
#include "chat.h"
#include "common.h"
#include "llama.h"

template<class T>
static std::string join(const std::vector<T> &values, const std::string &delim) {
    std::ostringstream str;
    for (size_t i = 0; i < values.size(); i++) {
        str << values[i];
        if (i < values.size() - 1) { str << delim; }
    }
    return str.str();
}

/**
 * LLama resources: context, model, batch and sampler
 */
constexpr int   N_THREADS_MIN           = 2;
constexpr int   N_THREADS_MAX           = 4;
constexpr int   N_THREADS_HEADROOM      = 2;

constexpr int   DEFAULT_CONTEXT_SIZE    = 4096;
constexpr int   OVERFLOW_HEADROOM       = 4;
constexpr int   DEFAULT_BATCH_SIZE      = 512;
constexpr float DEFAULT_SAMPLER_TEMP    = 0.3f;

static llama_model                      * g_model = nullptr;
static llama_context                    * g_context = nullptr;
static common_batch                       g_batch;
static common_chat_templates_ptr          g_chat_templates;
static common_sampler                   * g_sampler = nullptr;

static int                                g_active_n_ctx = DEFAULT_CONTEXT_SIZE;
static int                                g_active_n_batch = DEFAULT_BATCH_SIZE;
static std::atomic<bool>                  g_abort_requested{false};
static std::string                        g_last_system_prompt;

static bool aichat_abort_callback(void * /*data*/) {
    return g_abort_requested.load(std::memory_order_relaxed);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_init(JNIEnv *env, jobject /*unused*/, jstring nativeLibDir) {
    llama_log_set(aichat_android_log_callback, nullptr);

    const auto *path_to_backend = env->GetStringUTFChars(nativeLibDir, 0);
    LOGi("Loading backends from %s", path_to_backend);
    ggml_backend_load_all_from_path(path_to_backend);
    env->ReleaseStringUTFChars(nativeLibDir, path_to_backend);

    llama_backend_init();
    LOGi("Backend initiated; Log handler set.");
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_requestAbort(JNIEnv * /*env*/, jobject /*unused*/) {
    g_abort_requested.store(true, std::memory_order_relaxed);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_loadWithParams(
        JNIEnv *env,
        jobject /*unused*/,
        jstring jmodel_path,
        jboolean use_mmap
) {
    g_abort_requested.store(false, std::memory_order_relaxed);
    g_last_system_prompt.clear();

    llama_model_params model_params = llama_model_default_params();
    model_params.load_mode = use_mmap ? LLAMA_LOAD_MODE_MMAP : LLAMA_LOAD_MODE_NONE;

    const auto *model_path = env->GetStringUTFChars(jmodel_path, 0);
    LOGi("%s: Loading model file (use_mmap=%d)", __func__, (int) use_mmap);

    auto *model = llama_model_load_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(jmodel_path, model_path);
    if (!model) {
        return 1;
    }
    g_model = model;
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_load(JNIEnv *env, jobject thiz, jstring jmodel_path) {
    return Java_com_arm_aichat_internal_InferenceEngineImpl_loadWithParams(env, thiz, jmodel_path, JNI_TRUE);
}

static llama_context *init_context(
        llama_model *model,
        const int n_ctx = DEFAULT_CONTEXT_SIZE,
        const int n_threads_req = 0,
        const int n_threads_batch_req = 0,
        const int n_batch_req = DEFAULT_BATCH_SIZE,
        const bool flash_attn = false,
        const bool kv_q8_0 = false
) {
    if (!model) {
        LOGe("%s: model cannot be null", __func__);
        return nullptr;
    }

    const int default_threads = std::max(N_THREADS_MIN, std::min(N_THREADS_MAX,
                                                     (int) sysconf(_SC_NPROCESSORS_ONLN) -
                                                     N_THREADS_HEADROOM));
    const int n_threads = n_threads_req > 0 ? n_threads_req : default_threads;
    const int n_threads_batch = n_threads_batch_req > 0 ? n_threads_batch_req : n_threads;
    const int n_batch = n_batch_req > 0 ? n_batch_req : DEFAULT_BATCH_SIZE;

    llama_context_params ctx_params = llama_context_default_params();
    const int trained_context_size = llama_model_n_ctx_train(model);
    if (trained_context_size > 0 && n_ctx > trained_context_size) {
        LOGw("%s: Model was trained with %d context size, clamping from %d",
             __func__, trained_context_size, n_ctx);
    }
    const int effective_ctx = (trained_context_size > 0) ? std::min(n_ctx, trained_context_size) : n_ctx;

    ctx_params.n_ctx = effective_ctx;
    ctx_params.n_batch = n_batch;
    ctx_params.n_ubatch = n_batch;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads_batch;
    ctx_params.flash_attn_type = flash_attn ? LLAMA_FLASH_ATTN_TYPE_ENABLED : LLAMA_FLASH_ATTN_TYPE_DISABLED;
    if (kv_q8_0) {
        ctx_params.type_k = GGML_TYPE_Q8_0;
        ctx_params.type_v = GGML_TYPE_Q8_0;
    }
    ctx_params.abort_callback = aichat_abort_callback;
    ctx_params.abort_callback_data = nullptr;

    g_active_n_ctx = effective_ctx;
    g_active_n_batch = n_batch;

    auto *context = llama_init_from_model(model, ctx_params);
    if (context == nullptr && (kv_q8_0 || flash_attn)) {
        // Fallback to standard FP16 KV cache and disabled flash attention if model arch rejects experimental KV/FA
        LOGw("%s: Retrying context init with standard FP16 KV cache and flash_attn=disabled", __func__);
        ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_DISABLED;
        ctx_params.type_k = GGML_TYPE_F16;
        ctx_params.type_v = GGML_TYPE_F16;
        context = llama_init_from_model(model, ctx_params);
    }
    if (context == nullptr) {
        LOGe("%s: llama_init_from_model() returned null", __func__);
    }
    return context;
}

static common_sampler *new_sampler(
        const float temp,
        const float top_p = 0.95f,
        const int top_k = 40,
        const float repeat_penalty = 1.10f,
        const std::string &grammar_str = ""
) {
    common_params_sampling sparams;
    sparams.temp = temp;
    sparams.top_p = top_p;
    sparams.top_k = top_k;
    sparams.penalty_repeat = repeat_penalty;
    if (!grammar_str.empty()) {
        sparams.grammar = common_grammar(COMMON_GRAMMAR_TYPE_USER, grammar_str);
    }
    try {
        return common_sampler_init(g_model, sparams);
    } catch (const std::exception &e) {
        LOGw("%s: grammar sampler init failed (%s), falling back to standard sampler", __func__, e.what());
        sparams.grammar = common_grammar();
        return common_sampler_init(g_model, sparams);
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_prepareWithConfig(
        JNIEnv *env,
        jobject /*unused*/,
        jint n_ctx,
        jint n_threads,
        jint n_threads_batch,
        jint n_batch,
        jboolean flash_attn,
        jboolean kv_q8_0,
        jfloat temp,
        jfloat top_p,
        jint top_k,
        jfloat repeat_penalty,
        jstring jgrammar
) {
    auto *context = init_context(
            g_model,
            n_ctx > 0 ? (int) n_ctx : DEFAULT_CONTEXT_SIZE,
            (int) n_threads,
            (int) n_threads_batch,
            n_batch > 0 ? (int) n_batch : DEFAULT_BATCH_SIZE,
            flash_attn == JNI_TRUE,
            kv_q8_0 == JNI_TRUE
    );
    if (!context) { return 1; }
    g_context = context;
    g_batch = common_batch(context);
    try {
        g_chat_templates = common_chat_templates_init(g_model, "");
    } catch (...) {
        g_chat_templates.reset();
    }

    std::string grammar_str;
    if (jgrammar != nullptr) {
        const char *g_chars = env->GetStringUTFChars(jgrammar, nullptr);
        if (g_chars != nullptr) {
            grammar_str = g_chars;
            env->ReleaseStringUTFChars(jgrammar, g_chars);
        }
    }

    if (g_sampler != nullptr) {
        common_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    g_sampler = new_sampler(temp, top_p, top_k, repeat_penalty, grammar_str);
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_prepare(JNIEnv *env, jobject thiz) {
    return Java_com_arm_aichat_internal_InferenceEngineImpl_prepareWithConfig(
            env,
            thiz,
            DEFAULT_CONTEXT_SIZE,
            0,
            0,
            DEFAULT_BATCH_SIZE,
            JNI_FALSE,
            JNI_FALSE,
            DEFAULT_SAMPLER_TEMP,
            0.95f,
            40,
            1.10f,
            nullptr
    );
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativeUpdateSampler(
        JNIEnv *env,
        jobject /*unused*/,
        jfloat temp,
        jfloat top_p,
        jint top_k,
        jfloat repeat_penalty,
        jstring jgrammar
) {
    if (!g_model) return;
    std::string grammar_str;
    if (jgrammar != nullptr) {
        const char *g_chars = env->GetStringUTFChars(jgrammar, nullptr);
        if (g_chars != nullptr) {
            grammar_str = g_chars;
            env->ReleaseStringUTFChars(jgrammar, g_chars);
        }
    }
    if (g_sampler != nullptr) {
        common_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    g_sampler = new_sampler(temp, top_p, top_k, repeat_penalty, grammar_str);
}

static std::string get_backend() {
    std::vector<std::string> backends;
    for (size_t i = 0; i < ggml_backend_reg_count(); i++) {
        auto *reg = ggml_backend_reg_get(i);
        std::string name = ggml_backend_reg_name(reg);
        if (name != "CPU") {
            backends.push_back(ggml_backend_reg_name(reg));
        }
    }
    return backends.empty() ? "CPU" : join(backends, ",");
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_systemInfo(JNIEnv *env, jobject /*unused*/) {
    return env->NewStringUTF(llama_print_system_info());
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativeCountTokens(
        JNIEnv *env,
        jobject /*unused*/,
        jstring jtext
) {
    if (!g_context || !jtext) {
        return -1;
    }
    const char *chars = env->GetStringUTFChars(jtext, nullptr);
    if (!chars) return -1;
    std::string text(chars);
    env->ReleaseStringUTFChars(jtext, chars);
    const auto tokens = common_tokenize(g_context, text, false, true);
    return (jint) tokens.size();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_hasExplicitChatTemplate(
        JNIEnv * /*env*/,
        jobject /*unused*/
) {
    if (!g_chat_templates) return JNI_FALSE;
    return common_chat_templates_was_explicit(g_chat_templates.get()) ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_benchModel(JNIEnv *env, jobject /*unused*/, jint pp, jint tg,
                                                      jint pl, jint nr) {
    auto *context = init_context(g_model, pp);
    if (!context) {
        const auto *const err_msg = "Fail to init_context! Bench aborted.";
        LOGe(err_msg);
        return env->NewStringUTF(err_msg);
    }

    auto pp_avg = 0.0;
    auto tg_avg = 0.0;
    auto pp_std = 0.0;
    auto tg_std = 0.0;

    int i, j;
    int nri;
    for (nri = 0; nri < nr; nri++) {
        common_batch batch(context);

        const int n_tokens = pp;
        for (i = 0; i < n_tokens; i++) {
            batch.add(0, i, 0, false);
        }

        batch.set_output(batch.size() - 1, true);
        llama_memory_clear(llama_get_memory(context), false);

        const auto t_pp_start = ggml_time_us();
        if (llama_process(context, LLAMA_PROCESS_TYPE_DECODE, batch.get()) != 0) {
            LOGe("llama_decode() failed during prompt processing");
        }
        const auto t_pp_end = ggml_time_us();

        llama_memory_clear(llama_get_memory(context), false);
        const auto t_tg_start = ggml_time_us();
        for (i = 0; i < tg; i++) {
            batch.clear();
            for (j = 0; j < pl; j++) {
                batch.add(0, i, j, true);
            }

            if (llama_process(context, LLAMA_PROCESS_TYPE_DECODE, batch.get()) != 0) {
                LOGe("llama_decode() failed during text generation");
            }
        }
        const auto t_tg_end = ggml_time_us();

        llama_memory_clear(llama_get_memory(context), false);

        const auto t_pp = double(t_pp_end - t_pp_start) / 1000000.0;
        const auto t_tg = double(t_tg_end - t_tg_start) / 1000000.0;

        const auto speed_pp = double(pp) / t_pp;
        const auto speed_tg = double(pl * tg) / t_tg;

        pp_avg += speed_pp;
        tg_avg += speed_tg;

        pp_std += speed_pp * speed_pp;
        tg_std += speed_tg * speed_tg;
    }

    llama_free(context);

    pp_avg /= double(nr);
    tg_avg /= double(nr);

    if (nr > 1) {
        pp_std = sqrt(pp_std / double(nr - 1) - pp_avg * pp_avg * double(nr) / double(nr - 1));
        tg_std = sqrt(tg_std / double(nr - 1) - tg_avg * tg_avg * double(nr) / double(nr - 1));
    } else {
        pp_std = 0;
        tg_std = 0;
    }

    char model_desc[128];
    llama_model_desc(g_model, model_desc, sizeof(model_desc));

    const auto model_size = double(llama_model_size(g_model)) / 1024.0 / 1024.0 / 1024.0;
    const auto model_n_params = double(llama_model_n_params(g_model)) / 1e9;

    const auto backend = get_backend();
    std::stringstream result;
    result << std::setprecision(3);
    result << "| model | size | params | backend | test | t/s |\n";
    result << "| --- | --- | --- | --- | --- | --- |\n";
    result << "| " << model_desc << " | " << model_size << "GiB | " << model_n_params << "B | "
           << backend << " | pp " << pp << " | " << pp_avg << " ± " << pp_std << " |\n";
    result << "| " << model_desc << " | " << model_size << "GiB | " << model_n_params << "B | "
           << backend << " | tg " << tg << " | " << tg_avg << " ± " << tg_std << " |\n";
    return env->NewStringUTF(result.str().c_str());
}


/**
 * Completion loop's long-term states:
 * - chat management
 * - position tracking
 */
constexpr const char *ROLE_SYSTEM       = "system";
constexpr const char *ROLE_USER         = "user";
constexpr const char *ROLE_ASSISTANT    = "assistant";

static std::vector<common_chat_msg> chat_msgs;
static llama_pos system_prompt_position = 0;
static llama_pos current_position = 0;

static void reset_long_term_states(const bool clear_kv_cache = true) {
    chat_msgs.clear();
    system_prompt_position = 0;
    current_position = 0;
    g_last_system_prompt.clear();

    if (clear_kv_cache && g_context != nullptr) {
        llama_memory_clear(llama_get_memory(g_context), false);
    }
}

static void shift_context() {
    const int n_discard = std::max(1, (current_position - system_prompt_position) / 2);
    llama_memory_seq_rm(llama_get_memory(g_context), 0, system_prompt_position, system_prompt_position + n_discard);
    llama_memory_seq_add(llama_get_memory(g_context), 0, system_prompt_position + n_discard, current_position, -n_discard);
    current_position -= n_discard;
}

static std::string chat_add_and_format(const std::string &role, const std::string &content) {
    common_chat_msg new_msg;
    new_msg.role = role;
    new_msg.content = content;
    auto formatted = common_chat_format_single(
            g_chat_templates.get(), chat_msgs, new_msg, role == ROLE_USER, /* use_jinja */ false);
    chat_msgs.push_back(new_msg);
    // NOTE: Never log formatted prompt or message content (strict privacy rule)
    return formatted;
}

/**
 * Completion loop's short-term states
 */
static llama_pos stop_generation_position = 0;
static std::string cached_token_chars;
static std::ostringstream assistant_ss;

static void reset_short_term_states() {
    stop_generation_position = 0;
    cached_token_chars.clear();
    assistant_ss.str("");
}

static int decode_tokens_in_batches(
        llama_context *context,
        common_batch &batch,
        const llama_tokens &tokens,
        const llama_pos start_pos,
        const bool compute_last_logit = false) {
    const int batch_step = std::max(1, g_active_n_batch);
    const int ctx_limit = std::max(64, g_active_n_ctx);
    for (int i = 0; i < (int) tokens.size(); i += batch_step) {
        if (g_abort_requested.load(std::memory_order_relaxed)) {
            return 1;
        }
        const int cur_batch_size = std::min((int) tokens.size() - i, batch_step);
        batch.clear();

        if (start_pos + i + cur_batch_size >= ctx_limit - OVERFLOW_HEADROOM) {
            shift_context();
        }

        for (int j = 0; j < cur_batch_size; j++) {
            const llama_token token_id = tokens[i + j];
            const llama_pos position = start_pos + i + j;
            const bool want_logit = compute_last_logit && (i + j == (int) tokens.size() - 1);
            batch.add(token_id, position, 0, want_logit);
        }

        const int decode_result = llama_process(context, LLAMA_PROCESS_TYPE_DECODE, batch.get());
        if (decode_result) {
            if (!g_abort_requested.load(std::memory_order_relaxed)) {
                LOGe("%s: llama_decode failed w/ %d", __func__, decode_result);
            }
            return 1;
        }
    }
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_processSystemPrompt(
        JNIEnv *env,
        jobject /*unused*/,
        jstring jsystem_prompt
) {
    g_abort_requested.store(false, std::memory_order_relaxed);

    const auto *system_prompt = env->GetStringUTFChars(jsystem_prompt, nullptr);
    std::string raw_system_prompt(system_prompt ? system_prompt : "");
    env->ReleaseStringUTFChars(jsystem_prompt, system_prompt);

    // NEXUS PATCH (LLAMA_PATCHES.md #7): an empty system prompt means "reset the conversation":
    // clear chat history + KV cache and decode nothing, so the Kotlin layer can send a fully
    // pre-templated prompt for each turn without duplicated system/user turns in the KV cache.
    if (raw_system_prompt.empty()) {
        reset_long_term_states();
        reset_short_term_states();
        return 0;
    }

    // KV Cache prefix reuse: if the same system prompt is already at the start of the KV cache,
    // keep [0, system_prompt_position) and only clear tokens after system_prompt_position.
    if (!raw_system_prompt.empty() &&
        raw_system_prompt == g_last_system_prompt &&
        system_prompt_position > 0 &&
        current_position >= system_prompt_position) {
        if (current_position > system_prompt_position) {
            llama_memory_seq_rm(llama_get_memory(g_context), 0, system_prompt_position, -1);
            current_position = system_prompt_position;
        }
        if (chat_msgs.size() > 1) {
            chat_msgs.resize(1);
        }
        reset_short_term_states();
        return 0;
    }

    reset_long_term_states();
    reset_short_term_states();

    std::string formatted_system_prompt = raw_system_prompt;
    const bool has_chat_template = g_chat_templates && common_chat_templates_was_explicit(g_chat_templates.get());
    if (has_chat_template) {
        formatted_system_prompt = chat_add_and_format(ROLE_SYSTEM, raw_system_prompt);
    }

    const auto system_tokens = common_tokenize(g_context, formatted_system_prompt,
                                               has_chat_template, has_chat_template);

    const int max_batch_size = std::max(64, g_active_n_ctx - OVERFLOW_HEADROOM);
    if ((int) system_tokens.size() > max_batch_size) {
        LOGe("%s: System prompt exceeds context budget (%d > %d)",
             __func__, (int) system_tokens.size(), max_batch_size);
        return 1;
    }

    if (decode_tokens_in_batches(g_context, g_batch, system_tokens, current_position)) {
        return 2;
    }

    system_prompt_position = current_position = (int) system_tokens.size();
    g_last_system_prompt = raw_system_prompt;
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_processUserPrompt(
        JNIEnv *env,
        jobject /*unused*/,
        jstring juser_prompt,
        jint n_predict
) {
    g_abort_requested.store(false, std::memory_order_relaxed);
    reset_short_term_states();
    if (g_sampler != nullptr) {
        common_sampler_reset(g_sampler);
    }

    const auto *const user_prompt = env->GetStringUTFChars(juser_prompt, nullptr);
    std::string raw_user_prompt(user_prompt ? user_prompt : "");
    env->ReleaseStringUTFChars(juser_prompt, user_prompt);

    std::string formatted_user_prompt = raw_user_prompt;
    const bool has_chat_template = g_chat_templates && common_chat_templates_was_explicit(g_chat_templates.get());
    // Only apply native chat_add_and_format if the caller hasn't already formatted chat turns
    const bool already_templated = raw_user_prompt.find("<|im_start|>") != std::string::npos ||
                                   raw_user_prompt.find("<|start_header_id|>") != std::string::npos ||
                                   raw_user_prompt.find("<start_of_turn>") != std::string::npos ||
                                   raw_user_prompt.find("<|user|>") != std::string::npos ||
                                   raw_user_prompt.find("<\xEF\xBD\x9CUser\xEF\xBD\x9C>") != std::string::npos ||
                                   raw_user_prompt.find("[INST]") != std::string::npos;
    if (has_chat_template && !already_templated) {
        formatted_user_prompt = chat_add_and_format(ROLE_USER, raw_user_prompt);
    }

    auto user_tokens = common_tokenize(g_context, formatted_user_prompt, true, true);

    int user_prompt_size = (int) user_tokens.size();
    const int max_batch_size = std::max(64, g_active_n_ctx - system_prompt_position - OVERFLOW_HEADROOM);
    if (user_prompt_size > max_batch_size) {
        // Keep the latest tokens so recent context & assistant header remain intact
        user_tokens.erase(user_tokens.begin(), user_tokens.begin() + (user_prompt_size - max_batch_size));
        user_prompt_size = (int) user_tokens.size();
    }

    if (decode_tokens_in_batches(g_context, g_batch, user_tokens, current_position, true)) {
        return 2;
    }

    current_position += user_prompt_size;
    stop_generation_position = current_position + n_predict;
    return 0;
}

static bool is_valid_utf8(const char *string) {
    if (!string) { return true; }

    const auto *bytes = (const unsigned char *) string;
    int num;

    while (*bytes != 0x00) {
        if ((*bytes & 0x80) == 0x00) {
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            num = 4;
        } else {
            return false;
        }

        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
            bytes += 1;
        }
    }
    return true;
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_generateNextTokenBytes(
        JNIEnv *env,
        jobject /*unused*/
) {
    if (g_abort_requested.load(std::memory_order_relaxed)) {
        return nullptr;
    }

    const int ctx_limit = std::max(64, g_active_n_ctx);
    if (current_position >= ctx_limit - OVERFLOW_HEADROOM) {
        shift_context();
    }

    if (current_position >= stop_generation_position) {
        return nullptr;
    }

    const auto new_token_id = common_sampler_sample(g_sampler, g_context, -1);
    common_sampler_accept(g_sampler, new_token_id, true);

    g_batch.clear();
    g_batch.add(new_token_id, current_position, 0, true);
    if (llama_process(g_context, LLAMA_PROCESS_TYPE_DECODE, g_batch.get()) != 0) {
        return nullptr;
    }

    current_position++;

    if (llama_vocab_is_eog(llama_model_get_vocab(g_model), new_token_id)) {
        if (g_chat_templates && common_chat_templates_was_explicit(g_chat_templates.get())) {
            chat_add_and_format(ROLE_ASSISTANT, assistant_ss.str());
        }
        return nullptr;
    }

    auto new_token_chars = common_token_to_piece(g_context, new_token_id);
    assistant_ss << new_token_chars;

    jbyteArray out = env->NewByteArray((jsize) new_token_chars.size());
    if (out != nullptr && !new_token_chars.empty()) {
        env->SetByteArrayRegion(out, 0, (jsize) new_token_chars.size(),
                                reinterpret_cast<const jbyte *>(new_token_chars.data()));
    }
    return out;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_generateNextToken(
        JNIEnv *env,
        jobject /*unused*/
) {
    if (g_abort_requested.load(std::memory_order_relaxed)) {
        return nullptr;
    }

    const int ctx_limit = std::max(64, g_active_n_ctx);
    if (current_position >= ctx_limit - OVERFLOW_HEADROOM) {
        shift_context();
    }

    if (current_position >= stop_generation_position) {
        return nullptr;
    }

    const auto new_token_id = common_sampler_sample(g_sampler, g_context, -1);
    common_sampler_accept(g_sampler, new_token_id, true);

    g_batch.clear();
    g_batch.add(new_token_id, current_position, 0, true);
    if (llama_process(g_context, LLAMA_PROCESS_TYPE_DECODE, g_batch.get()) != 0) {
        return nullptr;
    }

    current_position++;

    if (llama_vocab_is_eog(llama_model_get_vocab(g_model), new_token_id)) {
        if (g_chat_templates && common_chat_templates_was_explicit(g_chat_templates.get())) {
            chat_add_and_format(ROLE_ASSISTANT, assistant_ss.str());
        }
        return nullptr;
    }

    auto new_token_chars = common_token_to_piece(g_context, new_token_id);
    cached_token_chars += new_token_chars;

    jstring result = nullptr;
    if (is_valid_utf8(cached_token_chars.c_str())) {
        result = env->NewStringUTF(cached_token_chars.c_str());
        assistant_ss << cached_token_chars;
        cached_token_chars.clear();
    } else {
        result = env->NewStringUTF("");
    }
    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_unload(JNIEnv * /*unused*/, jobject /*unused*/) {
    g_abort_requested.store(true, std::memory_order_relaxed);
    reset_long_term_states();
    reset_short_term_states();

    if (g_sampler != nullptr) {
        common_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    g_chat_templates.reset();
    g_batch = common_batch();
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_shutdown(JNIEnv *, jobject /*unused*/) {
    llama_backend_free();
}
