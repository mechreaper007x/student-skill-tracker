import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, importProvidersFrom, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { Activity, Award, Bot, Brain, Building2, ChevronDown, ChevronRight, ChevronUp, Code, Coffee, Crown, ExternalLink, Eye, FileCode, Flame, Folder, GitFork, Languages, Layout, LayoutDashboard, Loader2, Lock, LogOut, LucideAngularModule, Mail, Medal, Menu, Mic, Moon, Network, Package, Save, Send, Settings, Share2, Shield, Skull, Sparkles, Star, Sword, Swords, Target, Terminal, Trophy, User, X, Zap } from 'lucide-angular';

import { provideClientHydration, withEventReplay } from '@angular/platform-browser';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { errorInterceptor } from './core/auth/error.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideClientHydration(withEventReplay()),
    provideHttpClient(
      withInterceptors([authInterceptor, errorInterceptor]),
      withFetch()
    ),
    importProvidersFrom(
      LucideAngularModule.pick({
        Brain, LayoutDashboard, LogOut, Menu, Settings, Terminal, Trophy, X, Zap, Loader2,
        Mic, Eye, Coffee, Medal, Award, Bot, Sparkles, Send, Package, Layout,
        Mail, Lock, ChevronRight, ChevronUp, ChevronDown, User, Save, Star, GitFork,
        Sword, Flame, Shield, Languages, Skull, Building2, Crown, Target, Moon,
        Swords, Activity, Share2, Code, Network, Folder, FileCode, ExternalLink
      })
    )
  ]
};
