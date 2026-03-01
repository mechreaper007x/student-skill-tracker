import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, importProvidersFrom, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { Activity, ArrowUp, Award, Bot, Brain, Building2, CheckCircle2, ChevronDown, ChevronRight, ChevronUp, Circle, Code, Code2, Coffee, Cpu, Crown, ExternalLink, Eye, FileCode, FileJson, Flame, Folder, GitFork, HelpCircle, Info, Languages, Layout, LayoutDashboard, Lightbulb, Loader2, Lock, LogOut, LucideAngularModule, Mail, Medal, Menu, MessageSquare, Mic, Moon, Network, Package, Play, Plus, Puzzle, Radar, RotateCcw, Save, Search, SearchX, Send, Settings, Share2, Shield, ShieldCheck, Skull, Sparkles, Star, Sword, Swords, Target, Terminal, TerminalSquare, Trash2, Trophy, User, X, Zap } from 'lucide-angular';
import { MonacoEditorModule, NgxMonacoEditorConfig } from 'ngx-monaco-editor-v2';

import { provideClientHydration, withEventReplay } from '@angular/platform-browser';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { errorInterceptor } from './core/auth/error.interceptor';

const monacoConfig: NgxMonacoEditorConfig = {
  // Use absolute path so route depth never breaks Monaco loader resolution.
  baseUrl: '/assets/monaco/min/vs'
};

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
        Swords, Activity, Share2, Code, Code2, Network, Folder, FileCode, FileJson, ExternalLink, Info,
        Play, RotateCcw, Search, SearchX, Radar, MessageSquare, ArrowUp, ShieldCheck, Cpu, Plus, Trash2,
        Puzzle, Lightbulb, TerminalSquare, HelpCircle, Circle, CheckCircle2
      })
    ),
    importProvidersFrom(MonacoEditorModule.forRoot(monacoConfig))
  ]
};
