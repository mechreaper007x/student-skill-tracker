import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

export const roleGuard: CanActivateFn = (route) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const requiredRoles = (route.data?.['roles'] as string[] | undefined) ?? [];

  if (requiredRoles.length === 0) {
    return true;
  }

  if (authService.isAuthenticated() && authService.hasAnyRole(requiredRoles)) {
    return true;
  }

  return authService.checkAuth().pipe(
    map(user => {
      if (user && authService.hasAnyRole(requiredRoles)) {
        return true;
      }
      router.navigate(['/dashboard']);
      return false;
    })
  );
};
