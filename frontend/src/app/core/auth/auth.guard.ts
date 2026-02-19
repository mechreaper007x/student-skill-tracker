import { isPlatformBrowser } from '@angular/common';
import { inject, PLATFORM_ID } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const platformId = inject(PLATFORM_ID);

  // If on server, allow initial render so app can stabilize
  if (!isPlatformBrowser(platformId)) {
    return true;
  }

  // If already authenticated via signal, allow
  if (authService.isAuthenticated()) {
    return true;
  }

  // Otherwise check via service and redirect
  return authService.checkAuth().pipe(
    map(user => {
      if (user) return true;
      router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
      return false;
    })
  );
};
