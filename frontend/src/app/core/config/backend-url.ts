const DEFAULT_PROD_BACKEND_URL = 'https://student-skill-tracker-backend.onrender.com';

function sanitizeBaseUrl(value: string): string {
  return value.trim().replace(/\/+$/, '');
}

function isLocalHost(hostname: string): boolean {
  return hostname === 'localhost' || hostname === '127.0.0.1';
}

export function resolveBackendBaseUrl(): string {
  if (typeof window === 'undefined') {
    return '';
  }

  const runtimeConfigured =
    (window as any).__RISHI_BACKEND_BASE_URL__ ||
    (window as any).__BACKEND_URL__;

  if (typeof runtimeConfigured === 'string' && runtimeConfigured.trim().length > 0) {
    return sanitizeBaseUrl(runtimeConfigured);
  }

  if (isLocalHost(window.location.hostname.toLowerCase())) {
    return '';
  }

  return DEFAULT_PROD_BACKEND_URL;
}

