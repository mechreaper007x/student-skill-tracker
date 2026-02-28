import { isPlatformBrowser } from '@angular/common';
import { HttpInterceptorFn } from '@angular/common/http';
import { inject, PLATFORM_ID } from '@angular/core';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const platformId = inject(PLATFORM_ID);
  
  if (isPlatformBrowser(platformId)) {
    let token = localStorage.getItem('student_skill_tracker_jwt');
    
    if (token) {
      // Robust token cleaning: remove leading/trailing quotes if they exist
      token = token.trim();
      if (token.startsWith('"') && token.endsWith('"')) {
        token = token.substring(1, token.length - 1);
      }

      if (token) {
        const cloned = req.clone({
          setHeaders: {
            Authorization: `Bearer ${token}`
          },
          withCredentials: true
        });
        return next(cloned);
      }
    }

    // Keep credentials enabled for API requests so auth/fingerprint cookies flow correctly.
    if (req.url.startsWith('/api/') || req.url.includes('/api/')) {
      return next(req.clone({ withCredentials: true }));
    }
  }
  
  return next(req);
};
