import { bootstrapApplication } from '@angular/platform-browser';
import { App } from './app/app';
import { appConfig } from './app/app.config';

if (typeof window !== 'undefined') {
  (window as any).global = window;
}

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
