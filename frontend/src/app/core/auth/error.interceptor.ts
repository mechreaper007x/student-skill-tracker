import { isPlatformBrowser } from '@angular/common';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject, PLATFORM_ID } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const platformId = inject(PLATFORM_ID);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 || error.status === 403) {
        console.error('ErrorInterceptor: Unauthorized/Forbidden access detected', error);
        console.error('Error Body:', error.error);

        // Only redirect if not already on auth pages
        const currentUrl = router.url;
        const isAuthPage = currentUrl.includes('/login') || currentUrl.includes('/register');
        const requestUrl = error.url ?? req.url;
        const isOptionalIntegrationRequest =
          requestUrl.includes('/api/students/me/github-repos') ||
          requestUrl.includes('/api/students/me/leetcode-stats');

        // Force logout only for true session/auth failures.
        const shouldForceLogout = error.status === 401 && !isAuthPage && !isOptionalIntegrationRequest;

        if (shouldForceLogout) {
          if (isPlatformBrowser(platformId)) {
            localStorage.removeItem('student_skill_tracker_jwt');
          }
          router.navigate(['/login']);
        }
      }
      return throwError(() => error);
    })
  );
};
