import { ActivatedRouteSnapshot, DetachedRouteHandle, RouteReuseStrategy } from '@angular/router';

export class CustomRouteReuseStrategy implements RouteReuseStrategy {
  private handlers: { [key: string]: DetachedRouteHandle } = {};

  // Define which paths should be cached (matching app.routes.ts)
  private readonly routesToCache = ['arsenal', 'compiler'];

  /**
   * Determines if this route (and its descendants) should be detached to be reused later.
   */
  shouldDetach(route: ActivatedRouteSnapshot): boolean {
    if (!route.routeConfig || !route.routeConfig.path) {
      return false;
    }
    return this.routesToCache.includes(route.routeConfig.path);
  }

  /**
   * Stores the detached route.
   */
  store(route: ActivatedRouteSnapshot, handle: DetachedRouteHandle | null): void {
    if (route.routeConfig?.path) {
      if (handle) {
        this.handlers[route.routeConfig.path] = handle;
      } else {
        delete this.handlers[route.routeConfig.path];
      }
    }
  }

  /**
   * Determines if this route (and its descendants) should be reattached.
   */
  shouldAttach(route: ActivatedRouteSnapshot): boolean {
    if (!route.routeConfig || !route.routeConfig.path) {
      return false;
    }
    return !!this.handlers[route.routeConfig.path];
  }

  /**
   * Retrieves the previously stored route.
   */
  retrieve(route: ActivatedRouteSnapshot): DetachedRouteHandle | null {
    if (!route.routeConfig || !route.routeConfig.path) {
      return null;
    }
    return this.handlers[route.routeConfig.path] || null;
  }

  /**
   * Determines if a route should be reused.
   */
  shouldReuseRoute(future: ActivatedRouteSnapshot, curr: ActivatedRouteSnapshot): boolean {
    return future.routeConfig === curr.routeConfig;
  }
}
