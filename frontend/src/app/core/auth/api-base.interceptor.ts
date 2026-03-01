import { HttpInterceptorFn } from '@angular/common/http';
import { resolveBackendBaseUrl } from '../config/backend-url';

function isRelativeApiRequest(url: string): boolean {
  return url.startsWith('/api/');
}

export const apiBaseInterceptor: HttpInterceptorFn = (req, next) => {
  if (!isRelativeApiRequest(req.url)) {
    return next(req);
  }

  const backendBaseUrl = resolveBackendBaseUrl();
  if (!backendBaseUrl) {
    return next(req);
  }

  const rewrittenReq = req.clone({
    url: `${backendBaseUrl}${req.url}`
  });
  return next(rewrittenReq);
};

