(() => {
  'use strict';

  const REPO_OWNER = 'PAIN-hub';
  const REPO_NAME = 'zelkai';
  const LATEST_RELEASE_API = `https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases/latest`;
  const RELEASES_LIST_API = `https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases?per_page=1`;
  const FALLBACK_RELEASES_URL = `https://github.com/${REPO_OWNER}/${REPO_NAME}/releases`;
  const ALLOWED_DOWNLOAD_HOSTS = new Set([
    'github.com',
    'objects.githubusercontent.com',
    'release-assets.githubusercontent.com',
  ]);

  const downloadLinks = document.querySelectorAll('[data-download-link]');
  const primaryBtn = document.getElementById('download-apk-btn');
  const primaryBtnLabel = document.getElementById('download-btn-label');
  const releaseVersionEl = document.getElementById('release-version');
  const releaseSizeEl = document.getElementById('release-size');
  const downloadStatusEl = document.getElementById('download-status');
  const detailTagEl = document.getElementById('detail-tag');
  const detailDateEl = document.getElementById('detail-date');
  const detailFilenameEl = document.getElementById('detail-filename');
  const detailShaEl = document.getElementById('detail-sha256');
  const releasePageLink = document.getElementById('release-page-link');
  const copyShaBtn = document.getElementById('copy-sha-btn');
  const copyShaLabel = document.getElementById('copy-sha-label');

  let currentSha256 = '';

  function validateTrustedUrl(rawUrl) {
    if (typeof rawUrl !== 'string' || !rawUrl.trim()) return null;
    try {
      const parsed = new URL(rawUrl);
      if (parsed.protocol !== 'https:') return null;
      if (!ALLOWED_DOWNLOAD_HOSTS.has(parsed.hostname)) return null;
      return parsed.href;
    } catch {
      return null;
    }
  }

  function formatBytes(bytes) {
    if (typeof bytes !== 'number' || !Number.isFinite(bytes) || bytes <= 0) {
      return 'ARM64 APK';
    }
    const mb = bytes / (1024 * 1024);
    return `${mb.toFixed(1)} MB`;
  }

  function formatDate(isoString) {
    if (typeof isoString !== 'string') return 'Synced from GitHub Actions';
    const date = new Date(isoString);
    if (Number.isNaN(date.getTime())) return 'Synced from GitHub Actions';
    return new Intl.DateTimeFormat('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    }).format(date);
  }

  function extractSha256(bodyText) {
    if (typeof bodyText !== 'string') return '';
    const match = bodyText.match(/\b([a-fA-F0-9]{64})\b/);
    return match ? match[1].toLowerCase() : '';
  }

  function applyReleaseData(release) {
    if (!release || typeof release !== 'object') return;

    const assets = Array.isArray(release.assets) ? release.assets : [];
    const apkAsset = assets.find(
      (asset) =>
        asset &&
        typeof asset.name === 'string' &&
        asset.name.toLowerCase().endsWith('.apk')
    );

    const safeTag = typeof release.tag_name === 'string' && release.tag_name.trim()
      ? release.tag_name.trim()
      : 'latest';
    const safeReleaseUrl = validateTrustedUrl(release.html_url) || FALLBACK_RELEASES_URL;

    if (releaseVersionEl) releaseVersionEl.textContent = safeTag;
    if (detailTagEl) detailTagEl.textContent = safeTag;
    if (detailDateEl) detailDateEl.textContent = formatDate(release.published_at);
    if (releasePageLink) releasePageLink.setAttribute('href', safeReleaseUrl);

    if (apkAsset) {
      const safeDownloadUrl = validateTrustedUrl(apkAsset.browser_download_url);
      if (safeDownloadUrl) {
        downloadLinks.forEach((link) => {
          link.setAttribute('href', safeDownloadUrl);
          link.setAttribute('download', apkAsset.name);
        });
      }
      const sizeLabel = formatBytes(apkAsset.size);
      if (releaseSizeEl) releaseSizeEl.textContent = sizeLabel;
      if (detailFilenameEl) detailFilenameEl.textContent = apkAsset.name;
      if (downloadStatusEl) {
        downloadStatusEl.textContent = `Ready: ${apkAsset.name} (${sizeLabel}) — 16 KB ELF page-aligned ARM64 build.`;
      }
    } else if (downloadStatusEl) {
      downloadStatusEl.textContent = 'Latest release found on GitHub Releases. Select All Releases to view build assets.';
    }

    const sha = extractSha256(release.body);
    if (sha && detailShaEl && copyShaBtn) {
      currentSha256 = sha;
      detailShaEl.textContent = sha;
      copyShaBtn.disabled = false;
    }
  }

  async function hydrateLatestRelease() {
    try {
      let response = await fetch(LATEST_RELEASE_API, {
        headers: { Accept: 'application/vnd.github+json' },
      });

      if (response.status === 404) {
        response = await fetch(RELEASES_LIST_API, {
          headers: { Accept: 'application/vnd.github+json' },
        });
        if (!response.ok) return;
        const list = await response.json();
        if (Array.isArray(list) && list.length > 0) {
          applyReleaseData(list[0]);
        }
        return;
      }

      if (!response.ok) return;
      const data = await response.json();
      applyReleaseData(data);
    } catch {
      if (downloadStatusEl) {
        downloadStatusEl.textContent = 'Direct GitHub Releases download ready. Visit All Releases if your network blocks GitHub API metadata.';
      }
    }
  }

  if (primaryBtn && primaryBtnLabel && downloadStatusEl) {
    primaryBtn.addEventListener('click', () => {
      const originalText = primaryBtnLabel.textContent;
      primaryBtnLabel.textContent = 'Starting Download...';
      downloadStatusEl.textContent = 'Your browser is downloading the ZELKAI ARM64 APK from GitHub Releases.';
      window.setTimeout(() => {
        primaryBtnLabel.textContent = originalText;
      }, 2400);
    });
  }

  if (copyShaBtn && copyShaLabel) {
    copyShaBtn.addEventListener('click', async () => {
      if (!currentSha256 || !navigator.clipboard) return;
      try {
        await navigator.clipboard.writeText(currentSha256);
        copyShaLabel.textContent = 'Copied';
        window.setTimeout(() => {
          copyShaLabel.textContent = 'Copy';
        }, 1800);
      } catch {
        copyShaLabel.textContent = 'Copy failed';
      }
    });
  }

  // Native <dialog> screenshot lightbox
  const lightboxDialog = document.getElementById('lightbox-dialog');
  const lightboxImg = document.getElementById('lightbox-img');
  const lightboxTitle = document.getElementById('lightbox-title');
  const lightboxClose = document.getElementById('lightbox-close');
  const shotTriggers = document.querySelectorAll('.shot-trigger');

  if (lightboxDialog && lightboxImg && lightboxTitle) {
    shotTriggers.forEach((trigger) => {
      trigger.addEventListener('click', () => {
        const src = trigger.getAttribute('data-lightbox-src');
        const title = trigger.getAttribute('data-lightbox-title') || 'Screenshot Preview';
        if (!src || !src.startsWith('screenshots/')) return;
        lightboxImg.setAttribute('src', src);
        lightboxImg.setAttribute('alt', title);
        lightboxTitle.textContent = title;
        if (typeof lightboxDialog.showModal === 'function') {
          lightboxDialog.showModal();
        }
      });
    });

    if (lightboxClose) {
      lightboxClose.addEventListener('click', () => {
        lightboxDialog.close();
      });
    }

    lightboxDialog.addEventListener('click', (event) => {
      if (event.target === lightboxDialog) {
        lightboxDialog.close();
      }
    });
  }

  // Scroll-reveal fallback when CSS scroll-driven view() timeline is unsupported
  if (
    !CSS.supports('(animation-timeline: view()) and (animation-range: entry)') &&
    window.matchMedia('(prefers-reduced-motion: no-preference)').matches &&
    'IntersectionObserver' in window
  ) {
    const revealNodes = document.querySelectorAll('.reveal');
    revealNodes.forEach((node) => node.classList.add('reveal-pending'));

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible');
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.12 }
    );

    revealNodes.forEach((node) => observer.observe(node));
  }

  hydrateLatestRelease();
})();
