import { CommonModule, isPlatformBrowser } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, PLATFORM_ID, Pipe, PipeTransform, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { LucideAngularModule } from 'lucide-angular';
import mermaid from 'mermaid';

@Pipe({
  name: 'markdown',
  standalone: true
})
export class MarkdownPipe implements PipeTransform {
  private sanitizer = inject(DomSanitizer);

  transform(value: string): SafeHtml {
    if (!value) return '';
    
    let html = value
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
    
    // Handle Mermaid blocks first
    html = html.replace(/```mermaid\n?([\s\S]*?)```/g, '<div class="mermaid-container my-6 p-4 bg-noir-900/50 border border-noir-800 rounded-2xl overflow-x-auto"><pre class="mermaid">$1</pre></div>');

    // Code blocks
    html = html.replace(/```(?:[a-z]*)\n?([\s\S]*?)```/g, '<pre class="bg-black/60 p-5 rounded-2xl my-6 font-mono text-[11px] overflow-x-auto border border-noir-800 shadow-inner leading-relaxed">$1</pre>');
    html = html.replace(/`(.*?)`/g, '<code class="bg-noir-800 px-1.5 py-0.5 rounded text-crimson-400 font-mono text-[11px]">$1</code>');
    html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
    html = html.replace(/\*(.*?)\*/g, '<em>$1</em>');
    html = html.replace(/^\s*-\s+(.*)/gm, '<li class="ml-6 list-disc mb-1">$1</li>');

    const parts = html.split(/(<pre[\s\S]*?<\/pre>|<div[\s\S]*?<\/div>)/);
    html = parts.map(part => {
      if (part.startsWith('<pre') || part.startsWith('<div')) return part;
      return part.replace(/\n/g, '<br>');
    }).join('');

    return this.sanitizer.bypassSecurityTrustHtml(html);
  }
}

interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  type: 'text' | 'strategy' | 'init';
}

interface Thread {
  id: string;
  title: string;
  preview: string;
  updatedAt: string;
}

interface GenAiConfigResponse {
  provider: string;
  model: string;
  hasApiKey: boolean;
  maskedApiKey: string;
}

interface LearnResponse {
  reply: string;
  provider: string;
  model: string;
  threadId: string;
  memoryCount?: number;
}

interface AgentExecuteResponse {
  reply: string;
  provider: string;
  model: string;
  mode: string;
  threadId: string;
  actions?: string[];
}

interface AgentTaskEnqueueResponse {
  taskId: string;
  status: string;
}

interface AgentTaskPollResponse {
  status: string;
  response: AgentExecuteResponse;
  error: string;
}

interface StudyPlanResponse {
  hasPlan: boolean;
  topic: string;
  durationDays: number;
  generatedAt: string;
  plan: string;
  provider?: string;
  model?: string;
}

interface IntegrationTask {
  id: number;
  sourceType: string;
  title: string;
  details: string;
  topic: string;
  priority: number;
  status: string;
  suggestedMinutes: number;
  plannedStartAt: string;
  plannedEndAt: string;
  calendarEventLink: string;
  updatedAt: string;
}

interface GithubAnalytics {
  githubUsername: string;
  windowDays: number;
  commitCount: number;
  pullRequestCount: number;
  reviewCount: number;
  issueCount: number;
  activeRepoCount: number;
  totalStars: number;
  topLanguages: string;
  capturedAt: string;
}

interface LeetCodeAnalytics {
  leetcodeUsername: string;
  windowDays: number;
  totalSolved: number;
  easySolved: number;
  mediumSolved: number;
  hardSolved: number;
  ranking: number;
  reputation: number;
  contestRating: number;
  contestAttendedCount: number;
  solvedLast7d: number;
  solvedPrev7d: number;
  solveTrendPct: number;
  weakTopics: string;
  strongTopics: string;
  capturedAt: string;
}

interface CodeforcesAnalytics {
  codeforcesHandle: string;
  windowDays: number;
  currentRating: number;
  maxRating: number;
  rank: string;
  maxRank: string;
  contestCount: number;
  solvedTotal: number;
  solvedCurrentWindow: number;
  solvedPreviousWindow: number;
  solveTrendPct: number;
  strongTags: string;
  weakTags: string;
  capturedAt: string;
}

interface FocusMetrics {
  windowDays: number;
  plannedMinutes: number;
  actualMinutes: number;
  plannedSessions: number;
  actualSessions: number;
  adherencePct: number;
  dataSource: string;
}

interface TogglFocus {
  windowDays: number;
  trackedMinutes: number;
  entryCount: number;
  capturedAt: string;
}

interface AttemptCategoryTrend {
  category: string;
  currentCount: number;
  previousCount: number;
  sharePct: number;
  trendPct: number;
}

interface AttemptDailyTrend {
  date: string;
  attempts: number;
  successRatePct: number;
  averageAccuracyPct: number;
}

interface AttemptSourceBreakdown {
  source: string;
  attempts: number;
  successfulAttempts: number;
  successRatePct: number;
  averageAccuracyPct: number;
}

interface AttemptCategoryHeatmap {
  category: string;
  source: string;
  attempts: number;
  failedAttempts: number;
}

interface AttemptRecord {
  attemptedAt: string;
  source: string;
  success: boolean;
  failureBucket: string;
  accuracyPct: number;
  mistakeCategory: string;
  summary: string;
  nextSteps: string[];
}

interface AttemptHistoryResponse {
  days: number;
  limit: number;
  totalAttempts: number;
  successfulAttempts: number;
  successRatePct: number;
  averageAccuracyPct: number;
  attemptsGrowthPct: number;
  accuracyGrowthPct: number;
  categoryTrends: AttemptCategoryTrend[];
  recentAttempts: AttemptRecord[];
  dailyTrends: AttemptDailyTrend[];
  sourceBreakdown: AttemptSourceBreakdown[];
  categoryHeatmap: AttemptCategoryHeatmap[];
}

interface ActivityDailyTrend {
  date: string;
  typingMinutes: number;
  cursorIdleMinutes: number;
  editorUnfocusedMinutes: number;
  tabHiddenMinutes: number;
  activeMinutes: number;
}

interface ActivityBreakdownResponse {
  days: number;
  typingMinutes: number;
  cursorIdleMinutes: number;
  editorUnfocusedMinutes: number;
  tabHiddenMinutes: number;
  activeMinutes: number;
  totalTrackedMinutes: number;
  typingSharePct: number;
  cursorIdleSharePct: number;
  editorUnfocusedSharePct: number;
  tabHiddenSharePct: number;
  dailyTrends: ActivityDailyTrend[];
}

interface CoachingSummaryResponse {
  windowDays: number;
  pendingTasks: number;
  missedTasks: number;
  plannedMinutes: number;
  actualMinutes: number;
  adherencePct: number;
  recommendedMinutesToday: number;
  recommendedFocus: string;
  summary: string;
  generatedAt: string;
  nextActions: string[];
}

interface AttemptTrendPoint {
  index: number;
  accuracyPct: number;
  success: boolean;
  source: string;
  category: string;
  attemptedAt: string;
}

interface IntegrationStatusResponse {
  mode: 'chat' | 'agent';
  hasApiKey: boolean;
  googleCalendarConnected: boolean;
  githubConnected: boolean;
  leetcodeConnected: boolean;
  codeforcesConnected: boolean;
  togglConnected: boolean;
  googleCalendarId: string;
  codeforcesHandle: string;
  taskCount: number;
  pendingTaskCount: number;
  latestGithubAnalytics?: GithubAnalytics | null;
  latestLeetCodeAnalytics?: LeetCodeAnalytics | null;
  latestCodeforcesAnalytics?: CodeforcesAnalytics | null;
  latestTogglFocus?: TogglFocus | null;
  focusMetrics?: FocusMetrics | null;
  latestTasks: IntegrationTask[];
}

interface OAuthUrlResponse {
  provider: string;
  authUrl: string;
  state: string;
}

interface OAuthCompleteResponse {
  provider: string;
  status: IntegrationStatusResponse;
}

interface CalendarScheduleResponse {
  scheduledCount: number;
  items: Array<{
    taskId: number;
    taskTitle: string;
    startAt: string;
    endAt: string;
    eventId: string;
    eventLink: string;
  }>;
}

interface GithubAnalyticsSyncRequest {
  windowDays?: number;
}

interface LeetCodeAnalyticsSyncRequest {
  windowDays?: number;
}

interface CodeforcesAnalyticsSyncRequest {
  windowDays?: number;
}

interface TogglFocusSyncRequest {
  windowDays?: number;
}

@Component({
  selector: 'app-advisor',
  standalone: true,
  imports: [CommonModule, LucideAngularModule, FormsModule, MarkdownPipe],
  template: `
    <div class="h-[calc(100vh-6rem)] animate-fade-in flex overflow-hidden">
      
      <!-- SIDEBAR: Message History -->
      <aside class="w-80 border-r border-noir-800 bg-noir-950/50 hidden lg:flex flex-col p-6 gap-6">
        <div class="space-y-4">
          <button (click)="startNewThread()" class="w-full flex items-center gap-3 px-4 py-3 rounded-xl border border-dashed border-noir-700 text-noir-400 hover:text-white hover:border-crimson-500/50 hover:bg-crimson-500/5 transition-all group">
            <lucide-icon name="Plus" class="w-4 h-4 group-hover:rotate-90 transition-transform"></lucide-icon>
            <span class="text-[10px] font-black uppercase tracking-[0.2em]">New Neural Thread</span>
          </button>
        </div>

        <div class="flex-1 space-y-8 overflow-y-auto pr-2 custom-scrollbar">
          <div class="space-y-4">
            <div class="relative">
              <input type="text" [ngModel]="searchQuery()" (ngModelChange)="searchQuery.set($event)" placeholder="Search threads..." 
                class="w-full bg-noir-900 border border-noir-800 rounded-xl py-2 pl-9 pr-3 text-xs text-noir-200 placeholder:text-noir-600 focus:outline-none focus:border-crimson-500/50">
              <lucide-icon name="Search" class="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-noir-600"></lucide-icon>
            </div>
            
            <div class="flex items-center justify-between px-2">
              <p class="text-[9px] font-bold text-noir-600 uppercase tracking-widest">History</p>
              @if (threads().length > 0) {
                <button (click)="clearAllMemory()" class="text-[9px] font-bold text-crimson-500/70 hover:text-crimson-400 uppercase tracking-widest transition-colors">Clear All</button>
              }
            </div>
            <div class="space-y-1">
              @for (thread of filteredThreads(); track thread.id) {
                <div class="group relative flex items-center">
                  <button (click)="loadThread(thread.id)" 
                    class="w-full text-left px-3 py-2.5 rounded-lg transition-colors flex items-start gap-3"
                    [class.bg-noir-900]="activeThreadId() === thread.id"
                    [class.hover:bg-noir-900]="activeThreadId() !== thread.id"
                    [class.hover:bg-opacity-50]="activeThreadId() !== thread.id">
                    <lucide-icon name="MessageSquare" class="w-4 h-4 mt-0.5 shrink-0 transition-colors"
                      [class.text-crimson-500]="activeThreadId() === thread.id"
                      [class.text-noir-500]="activeThreadId() !== thread.id"
                      [class.group-hover:text-crimson-400]="activeThreadId() !== thread.id">
                    </lucide-icon>
                    <div class="overflow-hidden flex-1 pr-6">
                      <p class="text-xs font-semibold truncate transition-colors"
                         [class.text-white]="activeThreadId() === thread.id"
                         [class.text-noir-200]="activeThreadId() !== thread.id"
                         [class.group-hover:text-white]="activeThreadId() !== thread.id">
                        {{ thread.title }}
                      </p>
                      <p class="text-[10px] text-noir-500 mt-0.5 truncate">{{ thread.preview }}</p>
                    </div>
                  </button>
                  <button (click)="deleteThread(thread.id); $event.stopPropagation();" 
                    class="absolute right-2 opacity-30 group-hover:opacity-100 p-1.5 text-crimson-500 hover:bg-crimson-500/10 rounded-lg transition-all z-10" title="Delete Chat">
                    <lucide-icon name="Trash2" class="w-3.5 h-3.5"></lucide-icon>
                  </button>
                </div>
              }
              @if (threads().length === 0) {
                <p class="text-[10px] text-noir-600 px-2 italic">No previous threads found.</p>
              }
            </div>
          </div>
        </div>

        <div class="p-4 rounded-2xl bg-noir-900/40 border border-noir-800">
          <div class="flex items-center gap-3">
            <div class="w-2 h-2 rounded-full animate-pulse" [class.bg-emerald-500]="hasApiKey()" [class.bg-crimson-600]="!hasApiKey()"></div>
            <span class="text-[9px] font-bold text-noir-400 uppercase tracking-widest">{{ hasApiKey() ? 'Neural Link Synced' : 'Key Required' }}</span>
          </div>
        </div>
      </aside>

      <!-- MAIN CHAT AREA -->
      <main class="flex-1 flex flex-col bg-noir-950 relative">
        <header class="min-h-20 flex-wrap py-4 border-b border-noir-800 bg-noir-950/80 backdrop-blur-md flex items-center justify-between px-4 md:px-8 gap-4 z-20">
          <div class="flex items-center gap-4">
            <div class="p-2.5 rounded-xl bg-crimson-600 text-white shadow-lg shadow-crimson-900/20">
              <lucide-icon name="Brain" class="w-6 h-6"></lucide-icon>
            </div>
            <div>
              <h1 class="text-lg font-black tracking-tighter text-white uppercase italic">RISHI_v4.2</h1>
              <p class="text-[9px] text-noir-500 font-mono tracking-widest">MISTRAL_AI_RUNTIME // NEURAL_ADVISOR</p>
            </div>
          </div>

          <div class="flex flex-wrap items-center gap-2 md:gap-3">
            <div class="flex flex-wrap items-center gap-1 p-1 border border-noir-800 rounded-xl bg-noir-900 hidden sm:flex">
              <button
                (click)="setMode('chat')"
                class="px-3 py-1.5 text-[9px] font-black uppercase tracking-widest rounded-lg transition-all"
                [class]="mode() === 'chat' ? 'bg-crimson-600 text-white' : 'text-noir-400 hover:text-white'"
              >
                Chat Mode
              </button>
              <button
                (click)="setMode('agent')"
                class="px-3 py-1.5 text-[9px] font-black uppercase tracking-widest rounded-lg transition-all"
                [class]="mode() === 'agent' ? 'bg-emerald-600 text-white' : 'text-noir-400 hover:text-white'"
              >
                Agent Mode
              </button>
            </div>

            <button (click)="startMockInterview()" class="flex items-center gap-2 px-4 py-2 text-[10px] font-black uppercase tracking-widest border border-emerald-500/50 text-emerald-400 bg-noir-900 hover:bg-emerald-600/10 hover:text-emerald-300 hover:border-emerald-400 transition-all rounded-xl">
              <lucide-icon name="Mic" class="w-3.5 h-3.5"></lucide-icon>
              MOCK INTERVIEW
            </button>

            <div class="relative group">
              <select 
                [ngModel]="model()" 
                (ngModelChange)="model.set($event); onModelChange($event)"
                class="appearance-none bg-noir-900 border border-noir-800 rounded-xl px-4 py-2 pr-8 text-[10px] font-black uppercase tracking-widest text-noir-300 hover:text-white hover:border-crimson-500/40 transition-all cursor-pointer outline-none"
              >
                <option value="open-mixtral-8x7b">Mixtral 8x7B (Open)</option>
                <option value="mistral-small-latest">Mistral Small</option>
                <option value="mistral-medium-latest">Mistral Medium</option>
                <option value="mistral-large-latest">Mistral Large</option>
                <option value="pixtral-12b-2409">Pixtral 12B</option>
              </select>
              <lucide-icon name="ChevronDown" class="w-3 h-3 absolute right-3 top-1/2 -translate-y-1/2 text-noir-500 pointer-events-none"></lucide-icon>
            </div>

            <button (click)="toggleSettingsPanel()" class="p-2 md:p-2.5 rounded-xl border border-noir-800 bg-noir-900 text-noir-400 hover:text-white hover:border-crimson-500/40 transition-all">
              <lucide-icon name="Settings" class="w-5 h-5"></lucide-icon>
            </button>
          </div>
        </header>

        <div class="advisor-chat-scroll flex-1 overflow-y-auto px-4 md:px-12 py-6 md:py-12 space-y-8 md:space-y-12 scroll-smooth">
          @if (showSettings()) {
            <div class="max-w-3xl mx-auto noir-card p-8 space-y-6 animate-fade-in border-crimson-500/20 bg-noir-900/30 mb-8">
              <div class="flex items-center justify-between">
                <h3 class="text-sm font-black uppercase tracking-widest text-white">Neural Interface Parameters</h3>
                <button (click)="toggleSettingsPanel()" class="text-noir-500 hover:text-white"><lucide-icon name="X" class="w-4 h-4"></lucide-icon></button>
              </div>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div class="space-y-2">
                  <label class="text-[10px] font-bold text-noir-500 uppercase tracking-widest">Mistral API Key</label>
                  <div class="flex gap-2">
                    <input type="password" [value]="apiKeyInput()" (input)="updateApiKey($event)" [placeholder]="hasApiKey() ? ('••••' + maskedApiKey()) : 'Enter API Key'" 
                      class="flex-1 bg-black border border-noir-800 rounded-xl px-4 py-2.5 text-xs font-mono text-white focus:border-crimson-500/50 outline-none">
                    <button (click)="saveConfig()" [disabled]="isSavingConfig()" class="px-4 py-2 bg-crimson-600 text-white rounded-xl text-[10px] font-black uppercase hover:bg-crimson-500 transition-colors disabled:opacity-50">Save</button>
                  </div>
                </div>
                <div class="space-y-2">
                  <label class="text-[10px] font-bold text-noir-500 uppercase tracking-widest">Active Study Goal</label>
                  <input type="text" [(ngModel)]="planTopic" class="w-full bg-black border border-noir-800 rounded-xl px-4 py-2.5 text-xs font-mono text-white focus:border-crimson-500/50 outline-none">
                </div>
              </div>

              <div class="border border-noir-800 rounded-xl p-4 space-y-4 bg-black/30">
                <div class="flex items-center justify-between">
                  <p class="text-[10px] font-bold text-noir-400 uppercase tracking-widest">Rishi Integrations</p>
                  <span class="text-[9px] font-mono uppercase tracking-widest" [class]="mode() === 'agent' ? 'text-emerald-400' : 'text-noir-500'">
                    {{ mode() | uppercase }}
                  </span>
                </div>

                <div class="grid grid-cols-1 gap-3">
                  <div class="border border-noir-800 rounded-lg p-3">
                    <p class="text-[10px] font-semibold text-noir-300">Google Calendar</p>
                    <p class="text-[9px] text-noir-500 mt-1">{{ googleCalendarConnected() ? 'Connected' : 'Not connected' }}</p>
                    <div class="mt-3 flex gap-2 flex-wrap">
                      <button
                        *ngIf="!googleCalendarConnected()"
                        (click)="openGoogleCalendarConnect()"
                        [disabled]="isConnectingIntegration()"
                        class="px-3 py-1.5 text-[9px] font-black uppercase tracking-widest border border-emerald-500/40 text-emerald-300 hover:bg-emerald-600/10 disabled:opacity-40"
                      >
                        Connect
                      </button>
                      <button
                        *ngIf="googleCalendarConnected()"
                        (click)="scheduleCalendarBlocks()"
                        [disabled]="isSchedulingCalendar()"
                        class="px-3 py-1.5 text-[9px] font-black uppercase tracking-widest border border-noir-700 text-noir-300 hover:text-white disabled:opacity-40"
                      >
                        {{ isSchedulingCalendar() ? 'Scheduling...' : 'Schedule Next Blocks' }}
                      </button>
                      <button
                        *ngIf="googleCalendarConnected()"
                        (click)="autoRescheduleCalendarBlocks()"
                        [disabled]="isReschedulingCalendar()"
                        class="px-3 py-1.5 text-[9px] font-black uppercase tracking-widest border border-noir-700 text-noir-300 hover:text-white disabled:opacity-40"
                      >
                        {{ isReschedulingCalendar() ? 'Rescheduling...' : 'Auto Reschedule Missed' }}
                      </button>
                      <button
                        *ngIf="googleCalendarConnected()"
                        (click)="disconnectGoogleCalendar()"
                        class="px-3 py-1.5 text-[9px] font-black uppercase tracking-widest border border-crimson-500/40 text-crimson-300 hover:bg-crimson-600/10"
                      >
                        Disconnect
                      </button>
                    </div>
                    <div class="mt-3 border border-noir-800 rounded-md px-2.5 py-2" *ngIf="coachingSummary() || isLoadingCoachingSummary()">
                      <div class="flex items-center justify-between gap-2">
                        <p class="text-[9px] text-noir-300 uppercase tracking-widest">Daily Coaching</p>
                        <button
                          (click)="loadCoachingSummary(7)"
                          [disabled]="isLoadingCoachingSummary()"
                          class="px-2 py-1 text-[8px] font-black uppercase tracking-widest border border-noir-700 text-noir-400 hover:text-white disabled:opacity-40"
                        >
                          {{ isLoadingCoachingSummary() ? 'Loading...' : 'Refresh' }}
                        </button>
                      </div>
                      <p class="text-[9px] text-noir-500 mt-1" *ngIf="isLoadingCoachingSummary()">Synthesizing today's plan...</p>
                      <div *ngIf="coachingSummary() as coach" class="mt-2 space-y-1.5">
                        <p class="text-[9px] text-noir-300">{{ coach.summary }}</p>
                        <p class="text-[9px] text-amber-300">
                          Focus: {{ coach.recommendedFocus }} • {{ coach.recommendedMinutesToday }} min today
                        </p>
                        <ul class="list-disc pl-4 text-[9px] text-noir-400 space-y-0.5" *ngIf="coach.nextActions.length">
                          <li *ngFor="let action of coach.nextActions">{{ action }}</li>
                        </ul>
                      </div>
                    </div>
                  </div>

                  <div class="border border-noir-800 rounded-lg p-3">
                    <div class="flex items-center justify-between gap-3">
                      <p class="text-[10px] font-semibold text-noir-300">GitHub Analytics</p>
                      <span class="text-[9px] font-mono" [class]="githubConnected() ? 'text-emerald-400' : 'text-noir-500'">
                        {{ githubConnected() ? 'CONNECTED' : 'NOT CONNECTED' }}
                      </span>
                    </div>
                    <p class="text-[9px] text-noir-500 mt-1" *ngIf="githubAnalytics()">
                      {{ githubAnalytics()?.githubUsername }} • {{ githubAnalytics()?.windowDays }}d snapshot
                    </p>
                    <p class="text-[9px] text-noir-500 mt-1" *ngIf="!githubAnalytics()">
                      Link GitHub in Arsenal first, then sync analytics.
                    </p>
                    <div class="mt-3 flex gap-2 flex-wrap">
                      <button
                        (click)="syncGithubAnalytics()"
                        [disabled]="!githubConnected() || isSyncingGithubAnalytics()"
                        class="px-3 py-1.5 text-[9px] font-black uppercase tracking-widest border border-noir-700 text-noir-300 hover:text-white disabled:opacity-40"
                      >
                        {{ isSyncingGithubAnalytics() ? 'Syncing...' : 'Sync GitHub Metrics' }}
                      </button>
                    </div>
                    <div class="mt-3 grid grid-cols-2 md:grid-cols-3 gap-2" *ngIf="githubAnalytics()">
                      <div class="border border-noir-800 rounded-md px-2 py-1.5">
                        <p class="text-[8px] uppercase tracking-widest text-noir-500">Commits</p>
                        <p class="text-[11px] font-bold text-noir-200">{{ githubAnalytics()?.commitCount || 0 }}</p>
                      </div>
                      <div class="border border-noir-800 rounded-md px-2 py-1.5">
                        <p class="text-[8px] uppercase tracking-widest text-noir-500">PRs</p>
                        <p class="text-[11px] font-bold text-noir-200">{{ githubAnalytics()?.pullRequestCount || 0 }}</p>
                      </div>
                      <div class="border border-noir-800 rounded-md px-2 py-1.5">
                        <p class="text-[8px] uppercase tracking-widest text-noir-500">Reviews</p>
                        <p class="text-[11px] font-bold text-noir-200">{{ githubAnalytics()?.reviewCount || 0 }}</p>
                      </div>
                      <div class="border border-noir-800 rounded-md px-2 py-1.5">
                        <p class="text-[8px] uppercase tracking-widest text-noir-500">Issues</p>
                        <p class="text-[11px] font-bold text-noir-200">{{ githubAnalytics()?.issueCount || 0 }}</p>
                      </div>
                      <div class="border border-noir-800 rounded-md px-2 py-1.5">
                        <p class="text-[8px] uppercase tracking-widest text-noir-500">Repos</p>
                        <p class="text-[11px] font-bold text-noir-200">{{ githubAnalytics()?.activeRepoCount || 0 }}</p>
                      </div>
                      <div class="border border-noir-800 rounded-md px-2 py-1.5">
                        <p class="text-[8px] uppercase tracking-widest text-noir-500">Stars</p>
                        <p class="text-[11px] font-bold text-noir-200">{{ githubAnalytics()?.totalStars || 0 }}</p>
                      </div>
                    </div>
                    <p class="text-[9px] text-noir-500 mt-2 truncate" *ngIf="githubAnalytics()?.topLanguages">
                      Top languages: {{ githubAnalytics()?.topLanguages }}
                    </p>
                  </div>

                  <div class="border border-noir-800 rounded-lg p-3">
                    <div class="flex items-center justify-between gap-3">
                      <p class="text-[10px] font-semibold text-noir-300">LeetCode Analytics</p>
                      <span class="text-[9px] font-mono" [class]="leetcodeConnected() ? 'text-emerald-400' : 'text-noir-500'">
                        {{ leetcodeConnected() ? 'CONNECTED' : 'NOT CONNECTED' }}
                      </span>
                    </div>
                    <p class="text-[9px] text-noir-500 mt-1" *ngIf="leetcodeAnalytics()">
                      {{ leetcodeAnalytics()?.leetcodeUsername }} • {{ leetcodeAnalytics()?.windowDays }}d snapshot
                    </p>
                    <p class="text-[9px] text-noir-500 mt-1" *ngIf="!leetcodeAnalytics()">
                      Set your LeetCode username in profile, then sync analytics.
                    </p>
                    <div class="mt-3 flex gap-2 flex-wrap">
                      <button
                        (click)="syncLeetCodeAnalytics()"
                        [disabled]="!leetcodeConnected() || isSyncingLeetCodeAnalytics()"
                        class="px-3 py-1.5 text-[9px] font-black uppercase tracking-widest border border-noir-700 text-noir-300 hover:text-white disabled:opacity-40"
                      >
                        {{ isSyncingLeetCodeAnalytics() ? 'Syncing...' : 'Sync LeetCode Metrics' }}
                      </button>
                    </div>
                    <div class="mt-3 grid grid-cols-2 md:grid-cols-3 gap-2" *ngIf="leetcodeAnalytics()">
                      <div class="border border-noir-800 rounded-md px-2 py-1.5">
                        <p class="text-[8px] uppercase tracking-widest text-noir-500">Total Solved</p>
                        <p class="text-[11px] font-bold text-noir-200">{{ leetcodeAnalytics()?.totalSolved || 0 }}</p>
                      </div>
                      <div class="border border-noir-800 rounded-md px-2 py-1.5">
                        <p class="text-[8px] uppercase tracking-widest text-noir-500">Easy</p>
                        <p class="text-[11px] font-bold text-noir-200">{{ leetcodeAnalytics()?.easySolved || 0 }}</p>
                      </div>
                      <div class="border border-noir-800 rounded-md px-2 py-1.5">
                        <p class="text-[8px] uppercase tracking-widest text-noir-500">Medium</p>
                        <p class="text-[11px] font-bold text-noir-200">{{ leetcodeAnalytics()?.mediumSolved || 0 }}</p>
                      </div>
                      <div class="border border-noir-800 rounded-md px-2 py-1.5">
                        <p class="text-[8px] uppercase tracking-widest text-noir-500">Hard</p>
                        <p class="text-[11px] font-bold text-noir-200">{{ leetcodeAnalytics()?.hardSolved || 0 }}</p>
                      </div>
                      <div class="border border-noir-800 rounded-md px-2 py-1.5">
                        <p class="text-[8px] uppercase tracking-widest text-noir-500">Solved Last 7d</p>
                        <p class="text-[11px] font-bold text-noir-200">{{ leetcodeAnalytics()?.solvedLast7d || 0 }}</p>
                      </div>
                      <div class="border border-noir-800 rounded-md px-2 py-1.5">
                        <p class="text-[8px] uppercase tracking-widest text-noir-500">7d Trend</p>
                        <p
                          class="text-[11px] font-bold"
                          [class.text-emerald-300]="(leetcodeAnalytics()?.solveTrendPct ?? 0) >= 0"
                          [class.text-crimson-300]="(leetcodeAnalytics()?.solveTrendPct ?? 0) < 0"
                        >
                          {{ (leetcodeAnalytics()?.solveTrendPct ?? 0) | number:'1.0-1' }}%
                        </p>
                      </div>
                    </div>
                    <p class="text-[9px] text-noir-500 mt-2" *ngIf="leetcodeAnalytics()?.strongTopics">
                      Strong topics: {{ leetcodeAnalytics()?.strongTopics }}
                    </p>
                    <p class="text-[9px] text-noir-500 mt-1" *ngIf="leetcodeAnalytics()?.weakTopics">
                      Weak topics: {{ leetcodeAnalytics()?.weakTopics }}
                    </p>
                  </div>

                  <div class="border border-noir-800 rounded-lg p-3">
                    <div class="flex items-center justify-between gap-3">
                      <p class="text-[10px] font-semibold text-noir-300">Codeforces Analytics</p>
                      <span class="text-[9px] font-mono" [class]="codeforcesConnected() ? 'text-emerald-400' : 'text-noir-500'">
                        {{ codeforcesConnected() ? 'CONNECTED' : 'NOT CONNECTED' }}
                      </span>
                    </div>
                    <div class="mt-3 flex gap-2">
                      <input
                        type="text"
                        [ngModel]="codeforcesHandleInput()"
                        (ngModelChange)="codeforcesHandleInput.set($event)"
                        placeholder="Codeforces handle"
                        class="flex-1 bg-black border border-noir-800 rounded-md px-2.5 py-1.5 text-[10px] text-noir-200 placeholder:text-noir-600 focus:outline-none focus:border-crimson-500/40"
                      >
                      <button
                        (click)="saveCodeforcesHandle()"
                        [disabled]="isSavingCodeforcesHandle()"
                        class="px-3 py-1.5 text-[9px] font-black uppercase tracking-widest border border-emerald-500/40 text-emerald-300 hover:bg-emerald-600/10 disabled:opacity-40"
                      >
                        {{ isSavingCodeforcesHandle() ? 'Saving...' : 'Save' }}
                      </button>
                    </div>
                    <p class="text-[9px] text-noir-500 mt-2" *ngIf="codeforcesAnalytics()">
                      {{ codeforcesAnalytics()?.codeforcesHandle }} • {{ codeforcesAnalytics()?.windowDays }}d snapshot
                    </p>
                    <div class="mt-3 flex gap-2 flex-wrap">
                      <button
                        (click)="syncCodeforcesAnalytics()"
                        [disabled]="!codeforcesConnected() || isSyncingCodeforcesAnalytics()"
                        class="px-3 py-1.5 text-[9px] font-black uppercase tracking-widest border border-noir-700 text-noir-300 hover:text-white disabled:opacity-40"
                      >
                        {{ isSyncingCodeforcesAnalytics() ? 'Syncing...' : 'Sync Codeforces Metrics' }}
                      </button>
                    </div>
                    <div class="mt-3 grid grid-cols-2 md:grid-cols-3 gap-2" *ngIf="codeforcesAnalytics()">
                      <div class="border border-noir-800 rounded-md px-2 py-1.5">
                        <p class="text-[8px] uppercase tracking-widest text-noir-500">Rating</p>
                        <p class="text-[11px] font-bold text-noir-200">{{ codeforcesAnalytics()?.currentRating || 0 }}</p>
                      </div>
                      <div class="border border-noir-800 rounded-md px-2 py-1.5">
                        <p class="text-[8px] uppercase tracking-widest text-noir-500">Max Rating</p>
                        <p class="text-[11px] font-bold text-noir-200">{{ codeforcesAnalytics()?.maxRating || 0 }}</p>
                      </div>
                      <div class="border border-noir-800 rounded-md px-2 py-1.5">
                        <p class="text-[8px] uppercase tracking-widest text-noir-500">Contests</p>
                        <p class="text-[11px] font-bold text-noir-200">{{ codeforcesAnalytics()?.contestCount || 0 }}</p>
                      </div>
                      <div class="border border-noir-800 rounded-md px-2 py-1.5">
                        <p class="text-[8px] uppercase tracking-widest text-noir-500">Solved (All)</p>
                        <p class="text-[11px] font-bold text-noir-200">{{ codeforcesAnalytics()?.solvedTotal || 0 }}</p>
                      </div>
                      <div class="border border-noir-800 rounded-md px-2 py-1.5">
                        <p class="text-[8px] uppercase tracking-widest text-noir-500">Solved (Window)</p>
                        <p class="text-[11px] font-bold text-noir-200">{{ codeforcesAnalytics()?.solvedCurrentWindow || 0 }}</p>
                      </div>
                      <div class="border border-noir-800 rounded-md px-2 py-1.5">
                        <p class="text-[8px] uppercase tracking-widest text-noir-500">Trend</p>
                        <p
                          class="text-[11px] font-bold"
                          [class.text-emerald-300]="(codeforcesAnalytics()?.solveTrendPct ?? 0) >= 0"
                          [class.text-crimson-300]="(codeforcesAnalytics()?.solveTrendPct ?? 0) < 0"
                        >
                          {{ (codeforcesAnalytics()?.solveTrendPct ?? 0) | number:'1.0-1' }}%
                        </p>
                      </div>
                    </div>
                    <p class="text-[9px] text-noir-500 mt-2" *ngIf="codeforcesAnalytics()?.rank">
                      Rank: {{ codeforcesAnalytics()?.rank }} | Max Rank: {{ codeforcesAnalytics()?.maxRank || '-' }}
                    </p>
                    <p class="text-[9px] text-noir-500 mt-1" *ngIf="codeforcesAnalytics()?.strongTags">
                      Strong tags: {{ codeforcesAnalytics()?.strongTags }}
                    </p>
                    <p class="text-[9px] text-noir-500 mt-1" *ngIf="codeforcesAnalytics()?.weakTags">
                      Weak tags: {{ codeforcesAnalytics()?.weakTags }}
                    </p>
                  </div>

                  <div class="border border-noir-800 rounded-lg p-3">
                    <div class="flex items-center justify-between gap-3">
                      <p class="text-[10px] font-semibold text-noir-300">Toggl Focus Sync</p>
                      <span class="text-[9px] font-mono" [class]="togglConnected() ? 'text-emerald-400' : 'text-noir-500'">
                        {{ togglConnected() ? 'CONNECTED' : 'NOT CONNECTED' }}
                      </span>
                    </div>
                    <div class="mt-3 flex gap-2" *ngIf="!togglConnected()">
                      <input
                        type="password"
                        [ngModel]="togglTokenInput()"
                        (ngModelChange)="togglTokenInput.set($event)"
                        placeholder="Toggl API token"
                        class="flex-1 bg-black border border-noir-800 rounded-md px-2.5 py-1.5 text-[10px] text-noir-200 placeholder:text-noir-600 focus:outline-none focus:border-crimson-500/40"
                      >
                      <button
                        (click)="saveTogglToken()"
                        [disabled]="isSavingTogglToken()"
                        class="px-3 py-1.5 text-[9px] font-black uppercase tracking-widest border border-emerald-500/40 text-emerald-300 hover:bg-emerald-600/10 disabled:opacity-40"
                      >
                        {{ isSavingTogglToken() ? 'Saving...' : 'Connect' }}
                      </button>
                    </div>
                    <div class="mt-3 flex gap-2 flex-wrap" *ngIf="togglConnected()">
                      <button
                        (click)="syncTogglFocus()"
                        [disabled]="isSyncingTogglFocus()"
                        class="px-3 py-1.5 text-[9px] font-black uppercase tracking-widest border border-noir-700 text-noir-300 hover:text-white disabled:opacity-40"
                      >
                        {{ isSyncingTogglFocus() ? 'Syncing...' : 'Sync 7d Focus' }}
                      </button>
                      <button
                        (click)="disconnectToggl()"
                        class="px-3 py-1.5 text-[9px] font-black uppercase tracking-widest border border-crimson-500/40 text-crimson-300 hover:bg-crimson-600/10"
                      >
                        Disconnect
                      </button>
                    </div>
                    <p class="text-[9px] text-noir-500 mt-2" *ngIf="togglFocus()">
                      Last sync: {{ togglFocus()?.trackedMinutes || 0 }} minutes in {{ togglFocus()?.windowDays || 0 }}d ({{ togglFocus()?.entryCount || 0 }} entries)
                    </p>
                  </div>

                  <div class="border border-noir-800 rounded-lg p-3" *ngIf="focusMetrics()">
                    <div class="flex items-center justify-between gap-3">
                      <p class="text-[10px] font-semibold text-noir-300">Focus Adherence</p>
                      <span class="text-[9px] font-mono text-noir-500">
                        {{ focusMetrics()?.windowDays }}d
                      </span>
                    </div>
                    <div class="mt-3 grid grid-cols-2 md:grid-cols-3 gap-2">
                      <div class="border border-noir-800 rounded-md px-2 py-1.5">
                        <p class="text-[8px] uppercase tracking-widest text-noir-500">Planned Minutes</p>
                        <p class="text-[11px] font-bold text-noir-200">{{ focusMetrics()?.plannedMinutes || 0 }}</p>
                      </div>
                      <div class="border border-noir-800 rounded-md px-2 py-1.5">
                        <p class="text-[8px] uppercase tracking-widest text-noir-500">Actual Minutes</p>
                        <p class="text-[11px] font-bold text-noir-200">{{ focusMetrics()?.actualMinutes || 0 }}</p>
                      </div>
                      <div class="border border-noir-800 rounded-md px-2 py-1.5">
                        <p class="text-[8px] uppercase tracking-widest text-noir-500">Adherence</p>
                        <p
                          class="text-[11px] font-bold"
                          [class.text-emerald-300]="(focusMetrics()?.adherencePct ?? 0) >= 80"
                          [class.text-amber-300]="(focusMetrics()?.adherencePct ?? 0) >= 50 && (focusMetrics()?.adherencePct ?? 0) < 80"
                          [class.text-crimson-300]="(focusMetrics()?.adherencePct ?? 0) < 50"
                        >
                          {{ (focusMetrics()?.adherencePct ?? 0) | number:'1.0-1' }}%
                        </p>
                      </div>
                    </div>
                    <p class="text-[9px] text-noir-500 mt-2">
                      Sessions (planned/actual): {{ focusMetrics()?.plannedSessions || 0 }} / {{ focusMetrics()?.actualSessions || 0 }}
                    </p>
                    <p class="text-[9px] text-noir-600 mt-1" *ngIf="focusMetrics()?.dataSource">
                      Source: {{ focusMetrics()?.dataSource }}
                    </p>
                  </div>

                  <div class="border border-noir-800 rounded-lg p-3" *ngIf="activityBreakdown() || isLoadingActivityBreakdown()">
                    <div class="flex items-center justify-between gap-3">
                      <p class="text-[10px] font-semibold text-noir-300">Compiler Activity States</p>
                      <span class="text-[9px] font-mono text-noir-500">{{ activityBreakdown()?.days || attemptHistoryWindowDays() }}d</span>
                    </div>
                    <div class="mt-2 text-[9px] text-noir-500 font-mono" *ngIf="isLoadingActivityBreakdown()">
                      Loading state breakdown...
                    </div>
                    <div *ngIf="activityBreakdown() as breakdown" class="mt-3 space-y-3">
                      <div class="grid grid-cols-2 md:grid-cols-4 gap-2">
                        <div class="border border-noir-800 rounded-md px-2 py-1.5">
                          <p class="text-[8px] uppercase tracking-widest text-noir-500">Typing</p>
                          <p class="text-[11px] font-bold text-cyan-300">{{ breakdown.typingMinutes || 0 }}m</p>
                        </div>
                        <div class="border border-noir-800 rounded-md px-2 py-1.5">
                          <p class="text-[8px] uppercase tracking-widest text-noir-500">Cursor Idle</p>
                          <p class="text-[11px] font-bold text-emerald-300">{{ breakdown.cursorIdleMinutes || 0 }}m</p>
                        </div>
                        <div class="border border-noir-800 rounded-md px-2 py-1.5">
                          <p class="text-[8px] uppercase tracking-widest text-noir-500">Editor Unfocused</p>
                          <p class="text-[11px] font-bold text-amber-300">{{ breakdown.editorUnfocusedMinutes || 0 }}m</p>
                        </div>
                        <div class="border border-noir-800 rounded-md px-2 py-1.5">
                          <p class="text-[8px] uppercase tracking-widest text-noir-500">Tab Hidden</p>
                          <p class="text-[11px] font-bold text-rose-300">{{ breakdown.tabHiddenMinutes || 0 }}m</p>
                        </div>
                      </div>
                      <div class="space-y-1">
                        <div class="h-2 rounded bg-noir-900 overflow-hidden flex">
                          <div class="bg-cyan-500/80" [style.width.%]="toPercent(breakdown.typingSharePct || 0)"></div>
                          <div class="bg-emerald-500/80" [style.width.%]="toPercent(breakdown.cursorIdleSharePct || 0)"></div>
                          <div class="bg-amber-500/80" [style.width.%]="toPercent(breakdown.editorUnfocusedSharePct || 0)"></div>
                          <div class="bg-rose-500/80" [style.width.%]="toPercent(breakdown.tabHiddenSharePct || 0)"></div>
                        </div>
                        <p class="text-[8px] text-noir-500">
                          Active {{ breakdown.activeMinutes || 0 }}m / Total tracked {{ breakdown.totalTrackedMinutes || 0 }}m
                        </p>
                      </div>
                    </div>
                  </div>

                  <div class="border border-noir-800 rounded-lg p-3" *ngIf="attemptHistory() || isLoadingAttemptHistory()">
                    <div class="flex items-start justify-between gap-3">
                      <div>
                        <p class="text-[10px] font-semibold text-noir-300">Rishi Attempt History</p>
                        <p class="text-[8px] text-noir-600 mt-0.5">Compiler growth from tracked run/submit attempts</p>
                      </div>
                      <div class="flex items-center gap-1">
                        <button
                          *ngFor="let days of attemptHistoryWindows"
                          (click)="setAttemptHistoryWindow(days)"
                          [disabled]="isLoadingAttemptHistory()"
                          class="px-2 py-1 text-[8px] font-black uppercase tracking-widest border rounded disabled:opacity-40 transition-colors"
                          [class.border-crimson-500/60]="attemptHistoryWindowDays() === days"
                          [class.text-crimson-300]="attemptHistoryWindowDays() === days"
                          [class.bg-crimson-600/10]="attemptHistoryWindowDays() === days"
                          [class.border-noir-700]="attemptHistoryWindowDays() !== days"
                          [class.text-noir-400]="attemptHistoryWindowDays() !== days"
                          [class.hover:text-white]="attemptHistoryWindowDays() !== days"
                        >
                          {{ days }}d
                        </button>
                        <button
                          (click)="refreshAttemptHistory()"
                          [disabled]="isLoadingAttemptHistory()"
                          class="px-2 py-1 text-[8px] font-black uppercase tracking-widest border border-noir-700 text-noir-400 hover:text-white rounded disabled:opacity-40"
                        >
                          Refresh
                        </button>
                        <button
                          (click)="exportAttemptHistoryCsv()"
                          [disabled]="isLoadingAttemptHistory() || isExportingAttemptHistory()"
                          class="px-2 py-1 text-[8px] font-black uppercase tracking-widest border border-noir-700 text-noir-400 hover:text-white rounded disabled:opacity-40"
                        >
                          {{ isExportingAttemptHistory() ? 'Exporting...' : 'Export CSV' }}
                        </button>
                      </div>
                    </div>

                    <div class="mt-2 text-[9px] text-noir-500 font-mono" *ngIf="isLoadingAttemptHistory()">
                      Loading compiler attempt trends...
                    </div>

                    <div *ngIf="attemptHistory() as history" class="mt-3 space-y-3">
                      <div class="grid grid-cols-2 md:grid-cols-5 gap-2">
                        <div class="border border-noir-800 rounded-md px-2 py-1.5">
                          <p class="text-[8px] uppercase tracking-widest text-noir-500">Attempts</p>
                          <p class="text-[11px] font-bold text-noir-200">{{ history.totalAttempts || 0 }}</p>
                        </div>
                        <div class="border border-noir-800 rounded-md px-2 py-1.5">
                          <p class="text-[8px] uppercase tracking-widest text-noir-500">Success Rate</p>
                          <p class="text-[11px] font-bold text-noir-200">{{ (history.successRatePct || 0) | number:'1.0-1' }}%</p>
                        </div>
                        <div class="border border-noir-800 rounded-md px-2 py-1.5">
                          <p class="text-[8px] uppercase tracking-widest text-noir-500">Avg Accuracy</p>
                          <p class="text-[11px] font-bold text-noir-200">{{ (history.averageAccuracyPct || 0) | number:'1.0-1' }}%</p>
                        </div>
                        <div class="border border-noir-800 rounded-md px-2 py-1.5">
                          <p class="text-[8px] uppercase tracking-widest text-noir-500">Volume Trend</p>
                          <p
                            class="text-[11px] font-bold"
                            [class.text-emerald-300]="(history.attemptsGrowthPct || 0) >= 0"
                            [class.text-crimson-300]="(history.attemptsGrowthPct || 0) < 0"
                          >
                            {{ (history.attemptsGrowthPct || 0) | number:'1.0-1' }}%
                          </p>
                        </div>
                        <div class="border border-noir-800 rounded-md px-2 py-1.5">
                          <p class="text-[8px] uppercase tracking-widest text-noir-500">Accuracy Trend</p>
                          <p
                            class="text-[11px] font-bold"
                            [class.text-emerald-300]="(history.accuracyGrowthPct || 0) >= 0"
                            [class.text-crimson-300]="(history.accuracyGrowthPct || 0) < 0"
                          >
                            {{ (history.accuracyGrowthPct || 0) | number:'1.0-1' }}%
                          </p>
                        </div>
                      </div>

                      <div *ngIf="attemptTrendPoints().length">
                        <p class="text-[9px] text-noir-500 uppercase tracking-widest mb-1">Recent Accuracy Timeline</p>
                        <div class="h-16 border border-noir-800 rounded-md px-1.5 py-1 flex items-end gap-1 bg-black/40">
                          <div
                            *ngFor="let point of attemptTrendPoints()"
                            class="flex-1 min-w-[8px] rounded-sm transition-all"
                            [style.height.%]="toPercent(point.accuracyPct > 0 ? point.accuracyPct : (point.success ? 45 : 20))"
                            [class.bg-emerald-500/70]="point.success"
                            [class.bg-crimson-500/70]="!point.success"
                            [title]="(point.attemptedAt | date:'MMM d, h:mm a') + ' • ' + formatAttemptSource(point.source) + ' • ' + point.category + ' • ' + (point.accuracyPct | number:'1.0-1') + '%'"
                          ></div>
                        </div>
                        <div class="flex items-center gap-3 mt-1 text-[8px] text-noir-500">
                          <span class="flex items-center gap-1"><span class="w-2 h-2 rounded bg-emerald-500/70"></span>Success</span>
                          <span class="flex items-center gap-1"><span class="w-2 h-2 rounded bg-crimson-500/70"></span>Fail</span>
                        </div>
                      </div>

                      <div *ngIf="history.dailyTrends.length">
                        <p class="text-[9px] text-noir-500 uppercase tracking-widest mb-1">Daily Attempt Trend</p>
                        <div class="h-20 border border-noir-800 rounded-md px-1.5 py-1 flex items-end gap-1 bg-black/40">
                          <div
                            *ngFor="let day of history.dailyTrends.slice(-14)"
                            class="flex-1 min-w-[8px] rounded-sm transition-all bg-indigo-500/70"
                            [style.height.%]="toPercent((day.attempts || 0) * 12)"
                            [title]="day.date + ' • attempts: ' + day.attempts + ' • success: ' + (day.successRatePct | number:'1.0-1') + '%'"
                          ></div>
                        </div>
                      </div>

                      <div *ngIf="history.sourceBreakdown.length">
                        <p class="text-[9px] text-noir-500 uppercase tracking-widest mb-1">Source Split</p>
                        <div class="space-y-1.5">
                          <div *ngFor="let source of history.sourceBreakdown" class="border border-noir-800 rounded-md px-2 py-1.5">
                            <div class="flex items-center justify-between gap-2">
                              <p class="text-[8px] font-mono text-noir-300">{{ formatAttemptSource(source.source) }}</p>
                              <p class="text-[8px] font-mono text-noir-500">{{ source.attempts }} attempts</p>
                            </div>
                            <div class="mt-1 h-1.5 rounded bg-noir-900">
                              <div class="h-1.5 rounded bg-emerald-500/70" [style.width.%]="toPercent(source.successRatePct || 0)"></div>
                            </div>
                            <p class="text-[8px] text-noir-500 mt-1">
                              Success {{ source.successRatePct | number:'1.0-1' }}% • Accuracy {{ source.averageAccuracyPct | number:'1.0-1' }}%
                            </p>
                          </div>
                        </div>
                      </div>

                      <div *ngIf="history.categoryHeatmap.length">
                        <p class="text-[9px] text-noir-500 uppercase tracking-widest mb-1">Category Heatmap</p>
                        <div class="space-y-1 max-h-44 overflow-y-auto custom-scrollbar pr-1">
                          <div *ngFor="let cell of history.categoryHeatmap.slice(0, 24)" class="border border-noir-800 rounded-md px-2 py-1">
                            <div class="flex items-center justify-between gap-2">
                              <p class="text-[8px] text-noir-300">{{ cell.category }} • {{ formatAttemptSource(cell.source) }}</p>
                              <p class="text-[8px] font-mono text-noir-500">{{ cell.attempts }}</p>
                            </div>
                            <div class="mt-1 h-1 rounded bg-noir-900">
                              <div
                                class="h-1 rounded bg-crimson-500/70"
                                [style.width.%]="toPercent(cell.attempts > 0 ? (cell.failedAttempts * 100.0) / cell.attempts : 0)"
                              ></div>
                            </div>
                          </div>
                        </div>
                      </div>

                      <div *ngIf="history.categoryTrends.length">
                        <p class="text-[9px] text-noir-500 uppercase tracking-widest mb-1">Mistake Category Trends</p>
                        <div class="space-y-1.5">
                          <div *ngFor="let trend of history.categoryTrends.slice(0, 6)" class="border border-noir-800 rounded-md px-2 py-1.5">
                            <div class="flex items-center justify-between gap-2">
                              <p class="text-[8px] font-mono text-noir-300">{{ trend.category }}</p>
                              <span class="text-[8px] font-mono" [class]="(trend.trendPct || 0) <= 0 ? 'text-emerald-300' : 'text-amber-300'">
                                {{ (trend.trendPct || 0) | number:'1.0-1' }}%
                              </span>
                            </div>
                            <p class="text-[8px] text-noir-500">
                              {{ trend.currentCount }} now / {{ trend.previousCount }} prev
                            </p>
                            <div class="mt-1 h-1.5 rounded bg-noir-900">
                              <div class="h-1.5 rounded bg-crimson-500/70" [style.width.%]="toPercent(trend.sharePct || 0)"></div>
                            </div>
                          </div>
                        </div>
                      </div>

                      <div *ngIf="attemptSourceOptions().length > 1" class="space-y-1">
                        <p class="text-[9px] text-noir-500 uppercase tracking-widest">Source Filter</p>
                        <div class="flex flex-wrap gap-1.5">
                          <button
                            *ngFor="let source of attemptSourceOptions()"
                            (click)="setAttemptSourceFilter(source)"
                            class="px-2 py-1 text-[8px] font-black uppercase tracking-widest border rounded transition-colors"
                            [class.border-crimson-500/60]="attemptSourceFilter() === source"
                            [class.text-crimson-300]="attemptSourceFilter() === source"
                            [class.bg-crimson-600/10]="attemptSourceFilter() === source"
                            [class.border-noir-700]="attemptSourceFilter() !== source"
                            [class.text-noir-400]="attemptSourceFilter() !== source"
                            [class.hover:text-white]="attemptSourceFilter() !== source"
                          >
                            {{ source === 'ALL' ? 'ALL' : formatAttemptSource(source) }}
                          </button>
                        </div>
                      </div>

                      <div *ngIf="filteredRecentAttempts().length">
                        <p class="text-[9px] text-noir-500 uppercase tracking-widest mb-1">Latest Rishi Guidance</p>
                        <div class="space-y-2 max-h-52 overflow-y-auto custom-scrollbar pr-1">
                          <div *ngFor="let attempt of filteredRecentAttempts()" class="border border-noir-800 rounded-md px-2.5 py-2">
                            <div class="flex items-center justify-between gap-2">
                              <p class="text-[9px] text-noir-200">
                                {{ attempt.mistakeCategory }} • {{ attempt.failureBucket || 'NONE' }} • {{ formatAttemptSource(attempt.source) }}
                              </p>
                              <span class="text-[8px] font-mono" [class]="attempt.success ? 'text-emerald-300' : 'text-crimson-300'">
                                {{ attempt.success ? 'SUCCESS' : 'FAIL' }} • {{ attempt.accuracyPct | number:'1.0-1' }}%
                              </span>
                            </div>
                            <p class="text-[8px] text-noir-600 mt-1">{{ attempt.attemptedAt | date:'MMM d, h:mm a' }}</p>
                            <p class="text-[9px] text-noir-400 mt-1">{{ attempt.summary }}</p>
                            <p class="text-[9px] text-amber-300 mt-1" *ngIf="attempt.nextSteps.length">
                              Next: {{ attempt.nextSteps[0] }}
                            </p>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <div class="border border-noir-800 rounded-lg p-3" *ngIf="integrationTasks().length > 0">
                  <div class="flex items-center justify-between mb-2">
                    <p class="text-[10px] font-semibold text-noir-300">Imported Practice Tasks</p>
                    <span class="text-[9px] font-mono text-noir-500">{{ pendingTaskCount() }} pending</span>
                  </div>
                  <div class="space-y-2 max-h-44 overflow-y-auto custom-scrollbar pr-1">
                    <div *ngFor="let task of integrationTasks()" class="flex items-start justify-between gap-2 border border-noir-800 rounded-md px-2.5 py-2">
                      <div class="min-w-0">
                        <p class="text-[10px] text-noir-200 truncate">{{ task.title }}</p>
                        <p class="text-[9px] text-noir-500">{{ task.sourceType }} • {{ task.status }}</p>
                      </div>
                      <button
                        *ngIf="task.status !== 'DONE'"
                        (click)="markTaskDone(task.id)"
                        class="px-2 py-1 text-[8px] font-black uppercase tracking-widest border border-emerald-500/40 text-emerald-300 hover:bg-emerald-600/10"
                      >
                        Done
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              @if (hasStudyPlan()) {
                <div class="pt-4 border-t border-noir-800">
                  <div class="flex items-center justify-between mb-4">
                    <span class="text-[10px] font-bold text-crimson-500 uppercase tracking-widest">Active Plan: {{ studyPlanTopic() }}</span>
                    <button (click)="togglePlanExpansion()" class="text-[9px] uppercase text-noir-500 hover:text-white">
                      {{ isPlanExpanded() ? 'Collapse' : 'Expand' }}
                    </button>
                  </div>
                  @if (isPlanExpanded()) {
                    <div class="bg-black/50 rounded-xl p-4 border border-noir-800 max-h-64 overflow-y-auto custom-scrollbar">
                      <div class="text-xs text-noir-300 font-mono leading-relaxed" [innerHTML]="studyPlanText() | markdown"></div>
                    </div>
                  }
                </div>
              }
            </div>
          }

          @for (msg of messages(); track msg.id) {
            <div class="max-w-4xl mx-auto flex gap-6" [class.flex-row-reverse]="msg.role === 'user'">
              <div class="w-10 h-10 rounded-2xl flex items-center justify-center shrink-0 border transition-all"
                [class.bg-noir-800]="msg.role === 'user'" [class.border-noir-700]="msg.role === 'user'"
                [class.bg-crimson-600]="msg.role === 'assistant'" [class.border-crimson-500/30]="msg.role === 'assistant'"
                [class.shadow-lg]="msg.role === 'assistant'" [class.shadow-crimson-900/20]="msg.role === 'assistant'">
                @if (msg.role === 'user') { <span class="text-[10px] font-black text-noir-300">USER</span> } 
                @else { <lucide-icon name="Bot" class="w-5 h-5 text-white"></lucide-icon> }
              </div>

              <div class="group relative space-y-2 min-w-0" [class.text-right]="msg.role === 'user'">
                <div class="inline-block text-left max-w-[85vw] md:max-w-2xl rounded-2xl md:rounded-3xl p-4 md:p-6 transition-all"
                  [class.bg-noir-900]="msg.role === 'user'" [class.text-noir-100]="msg.role === 'user'" [class.rounded-tr-none]="msg.role === 'user'"
                  [class.bg-noir-900/40]="msg.role === 'assistant'" [class.text-noir-200]="msg.role === 'assistant'"
                  [class.border]="msg.role === 'assistant'" [class.border-noir-800]="msg.role === 'assistant'" [class.rounded-tl-none]="msg.role === 'assistant'">
                  <div class="prose prose-invert prose-sm max-w-none leading-relaxed font-sans" [innerHTML]="msg.content | markdown"></div>
                </div>
                
                @if (msg.type === 'strategy') {
                  <div class="mt-2 flex items-center gap-2 text-[9px] font-mono text-crimson-500 uppercase tracking-widest px-2 justify-end">
                    <lucide-icon name="Zap" class="w-3 h-3"></lucide-icon> Strategic Insight Fragmented
                  </div>
                }
              </div>
            </div>
          }

          @if (isGenerating() || isGeneratingPlan()) {
            <div class="max-w-4xl mx-auto flex gap-6">
              <div class="w-10 h-10 rounded-2xl bg-crimson-600 flex items-center justify-center shrink-0 animate-pulse">
                <lucide-icon name="Bot" class="w-5 h-5 text-white"></lucide-icon>
              </div>
              <div class="flex items-center gap-3 py-4">
                <div class="flex gap-1">
                  <div class="w-1.5 h-1.5 bg-crimson-500 rounded-full animate-bounce" style="animation-delay: 0s"></div>
                  <div class="w-1.5 h-1.5 bg-crimson-500 rounded-full animate-bounce" style="animation-delay: 0.2s"></div>
                  <div class="w-1.5 h-1.5 bg-crimson-500 rounded-full animate-bounce" style="animation-delay: 0.4s"></div>
                </div>
                <span class="text-[10px] font-mono text-noir-500 uppercase tracking-[0.2em]">Rishi is synthesizing...</span>
              </div>
            </div>
          }
        </div>

        <footer class="p-4 md:p-8 z-20">
          <div class="max-w-4xl mx-auto relative group">
            <div class="absolute -inset-1 bg-gradient-to-r from-crimson-600/20 to-noir-900/20 rounded-3xl blur opacity-0 group-focus-within:opacity-100 transition duration-500"></div>
            
            <div class="relative flex items-end gap-4 bg-noir-900 border border-noir-800 rounded-3xl p-4 focus-within:border-crimson-500/50 transition-all shadow-2xl">
              <button (click)="generateStrategy()" [disabled]="isGenerating()" 
                class="p-3 rounded-2xl text-noir-500 hover:text-crimson-500 hover:bg-crimson-500/5 transition-all">
                <lucide-icon name="Sparkles" class="w-6 h-6"></lucide-icon>
              </button>

              <textarea
                [(ngModel)]="currentInput"
                (keydown.enter)="$event.preventDefault(); sendMessage()"
                rows="1"
                [placeholder]="mode() === 'agent' ? 'Ask Rishi Agent to plan/schedule...' : 'Message Rishi...'"
                class="flex-1 bg-transparent border-none py-3 px-2 text-noir-100 placeholder:text-noir-600 focus:outline-none resize-none font-sans text-sm"
                (input)="autoGrow($event)"
              ></textarea>

              <button
                (click)="sendMessage()"
                [disabled]="isGenerating() || !currentInput.trim()"
                class="p-3 rounded-2xl bg-crimson-600 text-white disabled:opacity-20 disabled:grayscale hover:bg-crimson-500 transition-all shadow-lg shadow-crimson-900/20"
              >
                <lucide-icon name="ArrowUp" class="w-6 h-6"></lucide-icon>
              </button>
            </div>
            
            <div class="mt-4 flex justify-center gap-6">
              <span class="text-[9px] text-noir-600 uppercase tracking-widest font-bold flex items-center gap-2">
                <lucide-icon name="ShieldCheck" class="w-3 h-3"></lucide-icon> Private Context
              </span>
              <span class="text-[9px] text-noir-600 uppercase tracking-widest font-bold flex items-center gap-2">
                <lucide-icon name="Cpu" class="w-3 h-3"></lucide-icon> Distributed Mixtral
              </span>
            </div>
          </div>
        </footer>
      </main>
    </div>
  `,
  styles: [`
    :host { display: block; height: 100%; }
    .custom-scrollbar::-webkit-scrollbar { width: 4px; }
    .custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
    .custom-scrollbar::-webkit-scrollbar-thumb { background: #1a1a1a; border-radius: 10px; }
    .custom-scrollbar::-webkit-scrollbar-thumb:hover { background: #ef4444; }
    textarea { max-height: 200px; }
  `]
})
export class AdvisorComponent implements OnInit {
  private http = inject(HttpClient);
  private platformId = inject(PLATFORM_ID);

  private readonly greeting = 'Hello there. I am Rishi, your guide and mentor. I am here to help you understand concepts better and visualize complex ideas. How can we improve your skills today?';

  messages = signal<Message[]>([]);
  threads = signal<Thread[]>([]);
  searchQuery = signal<string>('');
  
  // Computed signal to filter threads based on the search query
  filteredThreads = computed(() => {
    const query = this.searchQuery().toLowerCase().trim();
    if (!query) {
      return this.threads();
    }
    return this.threads().filter(t => 
      t.title.toLowerCase().includes(query) || 
      t.preview.toLowerCase().includes(query)
    );
  });

  activeThreadId = signal<string | null>(null);
  
  currentInput = '';

  mode = signal<'chat' | 'agent'>('chat');

  provider = signal('mistral');
  model = signal('open-mixtral-8x7b');
  apiKeyInput = signal('');
  maskedApiKey = signal('');
  hasApiKey = signal(false);
  googleCalendarConnected = signal(false);
  githubConnected = signal(false);
  leetcodeConnected = signal(false);
  codeforcesConnected = signal(false);
  togglConnected = signal(false);
  githubAnalytics = signal<GithubAnalytics | null>(null);
  leetcodeAnalytics = signal<LeetCodeAnalytics | null>(null);
  codeforcesAnalytics = signal<CodeforcesAnalytics | null>(null);
  togglFocus = signal<TogglFocus | null>(null);
  focusMetrics = signal<FocusMetrics | null>(null);
  activityBreakdown = signal<ActivityBreakdownResponse | null>(null);
  attemptHistory = signal<AttemptHistoryResponse | null>(null);
  readonly attemptHistoryWindows: number[] = [7, 14, 30];
  attemptHistoryWindowDays = signal<number>(14);
  attemptSourceFilter = signal<string>('ALL');
  attemptSourceOptions = computed(() => {
    const history = this.attemptHistory();
    if (!history?.recentAttempts?.length) {
      return ['ALL'];
    }
    const sources = Array.from(new Set(history.recentAttempts.map((item) => item.source).filter((value) => !!value)));
    return ['ALL', ...sources];
  });
  filteredRecentAttempts = computed(() => {
    const history = this.attemptHistory();
    if (!history?.recentAttempts?.length) {
      return [];
    }
    const selectedSource = this.attemptSourceFilter();
    if (selectedSource === 'ALL') {
      return history.recentAttempts;
    }
    return history.recentAttempts.filter((item) => item.source === selectedSource);
  });
  attemptTrendPoints = computed<AttemptTrendPoint[]>(() => {
    const history = this.attemptHistory();
    if (!history?.recentAttempts?.length) {
      return [];
    }
    return history.recentAttempts
      .slice(0, 12)
      .reverse()
      .map((item, index) => ({
        index,
        accuracyPct: this.toPercent(item.accuracyPct || 0),
        success: !!item.success,
        source: item.source || '',
        category: item.mistakeCategory || 'Unknown',
        attemptedAt: item.attemptedAt
      }));
  });
  codeforcesHandleInput = signal('');
  togglTokenInput = signal('');
  pendingTaskCount = signal(0);
  integrationTasks = signal<IntegrationTask[]>([]);

  isSavingConfig = signal(false);
  isGenerating = signal(false);
  isGeneratingPlan = signal(false);
  isClearingMemory = signal(false);
  isSchedulingCalendar = signal(false);
  isSyncingGithubAnalytics = signal(false);
  isSyncingLeetCodeAnalytics = signal(false);
  isSavingCodeforcesHandle = signal(false);
  isSyncingCodeforcesAnalytics = signal(false);
  isSavingTogglToken = signal(false);
  isSyncingTogglFocus = signal(false);
  isLoadingActivityBreakdown = signal(false);
  isLoadingAttemptHistory = signal(false);
  isExportingAttemptHistory = signal(false);
  isReschedulingCalendar = signal(false);
  coachingSummary = signal<CoachingSummaryResponse | null>(null);
  isLoadingCoachingSummary = signal(false);
  isConnectingIntegration = signal(false);

  configStatus = signal<{ success: boolean; message: string } | null>(null);
  planStatus = signal<{ success: boolean; message: string } | null>(null);
  statusText = signal('AI Status: Key required');

  planTopic = 'Data Structures and Algorithms';
  planGoals = '';
  planDurationDays = 7;

  studyPlanText = signal('');
  studyPlanTopic = signal('');
  studyPlanDuration = signal(0);
  studyPlanGeneratedAt = signal('');
  hasStudyPlan = signal(false);
  isPlanExpanded = signal(false);
  showSettings = signal(false);

  constructor() {
    this.resetMessages();

    // Initialize Mermaid
    if (isPlatformBrowser(this.platformId)) {
      mermaid.initialize({
        startOnLoad: false,
        theme: 'dark',
        securityLevel: 'loose',
        fontFamily: 'monospace',
      });
    }

    // Effect to handle Mermaid rendering when messages update
    effect(() => {
      const msgs = this.messages();
      if (isPlatformBrowser(this.platformId) && msgs.length > 0) {
        setTimeout(() => {
          mermaid.run({
            nodes: document.querySelectorAll('.mermaid'),
          }).catch(err => console.error('Mermaid error:', err));
        }, 200);
      }
    });
  }

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    this.handleOAuthCallback();
    this.loadConfig();
    this.loadIntegrationsStatus();
    this.loadThreads();
    this.startNewThread(); // Always start with a blank new thread when opening
    this.loadStudyPlan();
    this.scrollToBottom();

    // Check for pending code reviews from Compiler
    setTimeout(() => {
      const pendingContext = sessionStorage.getItem('rishi_pending_context');
      if (pendingContext) {
        sessionStorage.removeItem('rishi_pending_context');
        this.addUserMessage(pendingContext, 'text');
        this.askRishi(pendingContext, 'text');
      }
    }, 500);
  }

  startNewThread() {
    this.activeThreadId.set(null);
    this.resetMessages();
    this.scrollToBottom();
  }

  startMockInterview() {
    this.startNewThread();
    const prompt = `Let's do a FAANG-style Live Mock Interview. Give me a medium-to-hard algorithmic problem. DO NOT give me any code or hints. I will first explain my approach and data structure choice. You must critique my approach, ask about edge cases, and ask about Big-O complexity BEFORE allowing me to write any code. Act exactly like a strict Senior Engineer interviewer.`;
    this.addUserMessage('Start a Live Mock Interview.', 'text');
    this.askRishi(prompt, 'text');
  }

  loadThreads() {
    this.http.get<Thread[]>('/api/advice/me/threads').subscribe({
      next: (data) => this.threads.set(data || []),
      error: () => this.threads.set([])
    });
  }

  loadThread(threadId: string) {
    this.activeThreadId.set(threadId);
    this.http.get<{ messages: any[] }>(`/api/advice/me/thread/${threadId}`).subscribe({
      next: (resp) => {
        const restored = (resp.messages || [])
          .filter((m) => !!m?.content && !!m?.role)
          .map((m, idx) => ({
            id: `${m.timestamp || Date.now()}-${idx}`,
            role: this.normalizeRole(m.role),
            content: m.content,
            type: this.normalizeType(m.type)
          } as Message));

        if (restored.length > 0) {
          this.messages.set(restored);
        } else {
          this.resetMessages();
        }
        this.scrollToBottom();
      },
      error: () => this.startNewThread()
    });
  }

  deleteThread(threadId: string) {
    this.http.delete(`/api/advice/me/thread/${threadId}`).subscribe({
      next: () => {
        this.loadThreads();
        if (this.activeThreadId() === threadId) {
          this.startNewThread();
        }
      },
      error: () => {}
    });
  }

  clearAllMemory() {
    if (confirm('Are you sure you want to delete all neural threads? This cannot be undone.')) {
      this.http.post('/api/advice/me/memory/clear', {}).subscribe({
        next: () => {
          this.loadThreads();
          this.startNewThread();
        },
        error: () => {}
      });
    }
  }

  loadConfig() {
    this.http.get<GenAiConfigResponse>('/api/advice/me/genai-config').subscribe({
      next: (config) => {
        this.provider.set(config.provider || 'mistral');
        this.model.set(config.model || 'open-mixtral-8x7b');
        this.hasApiKey.set(!!config.hasApiKey);
        this.maskedApiKey.set(config.maskedApiKey || '');
        this.updateStatus();
      },
      error: () => this.updateStatus()
    });
  }

  loadIntegrationsStatus() {
    this.http.get<IntegrationStatusResponse>('/api/rishi/integrations/status').subscribe({
      next: (status) => {
        this.mode.set(status.mode || 'chat');
        this.googleCalendarConnected.set(!!status.googleCalendarConnected);
        this.githubConnected.set(!!status.githubConnected);
        this.leetcodeConnected.set(!!status.leetcodeConnected);
        this.codeforcesConnected.set(!!status.codeforcesConnected);
        this.togglConnected.set(!!status.togglConnected);
        this.githubAnalytics.set(status.latestGithubAnalytics || null);
        this.leetcodeAnalytics.set(status.latestLeetCodeAnalytics || null);
        this.codeforcesAnalytics.set(status.latestCodeforcesAnalytics || null);
        this.togglFocus.set(status.latestTogglFocus || null);
        this.focusMetrics.set(status.focusMetrics || null);
        this.codeforcesHandleInput.set(status.codeforcesHandle || '');
        this.togglTokenInput.set('');
        this.pendingTaskCount.set(status.pendingTaskCount || 0);
        this.integrationTasks.set(status.latestTasks || []);
        this.loadAttemptHistory(this.attemptHistoryWindowDays(), this.getAttemptHistoryLimit(this.attemptHistoryWindowDays()));
        this.loadActivityBreakdown(this.attemptHistoryWindowDays());
        this.loadCoachingSummary(7);
      },
      error: () => {
        this.googleCalendarConnected.set(false);
        this.githubConnected.set(false);
        this.leetcodeConnected.set(false);
        this.codeforcesConnected.set(false);
        this.togglConnected.set(false);
        this.githubAnalytics.set(null);
        this.leetcodeAnalytics.set(null);
        this.codeforcesAnalytics.set(null);
        this.togglFocus.set(null);
        this.focusMetrics.set(null);
        this.activityBreakdown.set(null);
        this.attemptHistory.set(null);
        this.attemptSourceFilter.set('ALL');
        this.coachingSummary.set(null);
        this.isLoadingActivityBreakdown.set(false);
        this.isLoadingAttemptHistory.set(false);
        this.isLoadingCoachingSummary.set(false);
      }
    });
  }

  loadAttemptHistory(days: number = 14, limit: number = 12) {
    this.isLoadingAttemptHistory.set(true);
    this.http.get<AttemptHistoryResponse>(`/api/rishi/coding/attempt-history?days=${days}&limit=${limit}`).subscribe({
      next: (resp) => {
        this.isLoadingAttemptHistory.set(false);
        this.attemptHistory.set(resp || null);
        const allowedSources = new Set((resp?.recentAttempts || []).map((item) => item.source));
        if (this.attemptSourceFilter() !== 'ALL' && !allowedSources.has(this.attemptSourceFilter())) {
          this.attemptSourceFilter.set('ALL');
        }
      },
      error: () => {
        this.isLoadingAttemptHistory.set(false);
        this.attemptHistory.set(null);
        this.attemptSourceFilter.set('ALL');
      }
    });
  }

  loadActivityBreakdown(days: number = 14) {
    this.isLoadingActivityBreakdown.set(true);
    this.http.get<ActivityBreakdownResponse>(`/api/rishi/coding/activity-breakdown?days=${days}`).subscribe({
      next: (resp) => {
        this.isLoadingActivityBreakdown.set(false);
        this.activityBreakdown.set(resp || null);
      },
      error: () => {
        this.isLoadingActivityBreakdown.set(false);
        this.activityBreakdown.set(null);
      }
    });
  }

  loadCoachingSummary(days: number = 7) {
    this.isLoadingCoachingSummary.set(true);
    this.http.get<CoachingSummaryResponse>(`/api/rishi/integrations/coaching-summary?days=${days}`).subscribe({
      next: (resp) => {
        this.isLoadingCoachingSummary.set(false);
        this.coachingSummary.set(resp || null);
      },
      error: () => {
        this.isLoadingCoachingSummary.set(false);
        this.coachingSummary.set(null);
      }
    });
  }

  setAttemptHistoryWindow(days: number) {
    if (!this.attemptHistoryWindows.includes(days)) {
      return;
    }
    if (this.attemptHistoryWindowDays() === days && this.attemptHistory()) {
      return;
    }
    this.attemptHistoryWindowDays.set(days);
    this.loadAttemptHistory(days, this.getAttemptHistoryLimit(days));
  }

  refreshAttemptHistory() {
    this.loadAttemptHistory(this.attemptHistoryWindowDays(), this.getAttemptHistoryLimit(this.attemptHistoryWindowDays()));
    this.loadActivityBreakdown(this.attemptHistoryWindowDays());
  }

  setAttemptSourceFilter(source: string) {
    this.attemptSourceFilter.set(source || 'ALL');
  }

  exportAttemptHistoryCsv() {
    if (this.isExportingAttemptHistory()) {
      return;
    }
    this.isExportingAttemptHistory.set(true);
    const days = this.attemptHistoryWindowDays();
    this.http.get(`/api/rishi/coding/attempt-history/export?days=${days}`, { responseType: 'text' }).subscribe({
      next: (csv) => {
        this.isExportingAttemptHistory.set(false);
        if (!isPlatformBrowser(this.platformId)) {
          return;
        }
        const blob = new Blob([csv || ''], { type: 'text/csv;charset=utf-8' });
        const url = window.URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `rishi_attempt_history_${days}d.csv`;
        anchor.click();
        window.URL.revokeObjectURL(url);
      },
      error: (err: HttpErrorResponse) => {
        this.isExportingAttemptHistory.set(false);
        this.planStatus.set({ success: false, message: this.extractError(err, 'Failed to export attempt history.') });
      }
    });
  }

  setMode(mode: 'chat' | 'agent') {
    if (this.mode() === mode) {
      return;
    }
    this.mode.set(mode);
    this.http.post<{ mode: 'chat' | 'agent' }>('/api/rishi/integrations/mode', { mode }).subscribe({
      next: (resp) => this.mode.set(resp?.mode || mode),
      error: () => this.mode.set(mode)
    });
  }

  openGoogleCalendarConnect() {
    this.startOAuthFlow('/api/rishi/integrations/google-calendar/auth-url');
  }

  disconnectGoogleCalendar() {
    this.http.post('/api/rishi/integrations/google-calendar/disconnect', {}).subscribe({
      next: () => this.loadIntegrationsStatus(),
      error: () => {}
    });
  }

  scheduleCalendarBlocks() {
    if (this.isSchedulingCalendar()) {
      return;
    }
    this.isSchedulingCalendar.set(true);
    this.http.post<CalendarScheduleResponse>('/api/rishi/integrations/google-calendar/schedule', {
      count: 2,
      blockMinutes: 50
    }).subscribe({
      next: (resp) => {
        this.isSchedulingCalendar.set(false);
        this.planStatus.set({
          success: true,
          message: `Scheduled ${resp.scheduledCount} study block(s) in Google Calendar.`
        });
        this.loadIntegrationsStatus();
      },
      error: (err: HttpErrorResponse) => {
        this.isSchedulingCalendar.set(false);
        this.planStatus.set({ success: false, message: this.extractError(err, 'Calendar scheduling failed.') });
      }
    });
  }

  autoRescheduleCalendarBlocks() {
    if (this.isReschedulingCalendar()) {
      return;
    }
    this.isReschedulingCalendar.set(true);
    this.http.post<CalendarScheduleResponse>('/api/rishi/integrations/google-calendar/auto-reschedule', {
      count: 2,
      blockMinutes: 50
    }).subscribe({
      next: (resp) => {
        this.isReschedulingCalendar.set(false);
        this.planStatus.set({
          success: true,
          message: `Auto-rescheduled and scheduled ${resp.scheduledCount} study block(s).`
        });
        this.loadIntegrationsStatus();
      },
      error: (err: HttpErrorResponse) => {
        this.isReschedulingCalendar.set(false);
        this.planStatus.set({ success: false, message: this.extractError(err, 'Auto-reschedule failed.') });
      }
    });
  }

  syncGithubAnalytics(windowDays: number = 30) {
    if (this.isSyncingGithubAnalytics()) {
      return;
    }
    if (!this.githubConnected()) {
      this.planStatus.set({ success: false, message: 'Link GitHub in Arsenal before syncing analytics.' });
      return;
    }

    this.isSyncingGithubAnalytics.set(true);
    const payload: GithubAnalyticsSyncRequest = { windowDays };
    this.http.post<GithubAnalytics>('/api/rishi/integrations/github/analytics/sync', payload).subscribe({
      next: (resp) => {
        this.isSyncingGithubAnalytics.set(false);
        this.githubAnalytics.set(resp || null);
        this.planStatus.set({
          success: true,
          message: `GitHub analytics synced (${resp?.windowDays || windowDays}d): ${resp?.commitCount || 0} commits, ${resp?.pullRequestCount || 0} PRs.`
        });
        this.loadIntegrationsStatus();
      },
      error: (err: HttpErrorResponse) => {
        this.isSyncingGithubAnalytics.set(false);
        this.planStatus.set({ success: false, message: this.extractError(err, 'GitHub analytics sync failed.') });
      }
    });
  }

  syncLeetCodeAnalytics(windowDays: number = 30) {
    if (this.isSyncingLeetCodeAnalytics()) {
      return;
    }
    if (!this.leetcodeConnected()) {
      this.planStatus.set({ success: false, message: 'Set your LeetCode username before syncing analytics.' });
      return;
    }

    this.isSyncingLeetCodeAnalytics.set(true);
    const payload: LeetCodeAnalyticsSyncRequest = { windowDays };
    this.http.post<LeetCodeAnalytics>('/api/rishi/integrations/leetcode/analytics/sync', payload).subscribe({
      next: (resp) => {
        this.isSyncingLeetCodeAnalytics.set(false);
        this.leetcodeAnalytics.set(resp || null);
        this.planStatus.set({
          success: true,
          message: `LeetCode analytics synced (${resp?.windowDays || windowDays}d): ${resp?.totalSolved || 0} solved, 7d trend ${resp?.solveTrendPct?.toFixed(1) || '0.0'}%.`
        });
        this.loadIntegrationsStatus();
      },
      error: (err: HttpErrorResponse) => {
        this.isSyncingLeetCodeAnalytics.set(false);
        this.planStatus.set({ success: false, message: this.extractError(err, 'LeetCode analytics sync failed.') });
      }
    });
  }

  saveCodeforcesHandle() {
    if (this.isSavingCodeforcesHandle()) {
      return;
    }
    const handle = this.codeforcesHandleInput().trim();
    this.isSavingCodeforcesHandle.set(true);
    this.http.post<{ codeforcesHandle: string }>('/api/rishi/integrations/codeforces/handle', { handle }).subscribe({
      next: (resp) => {
        this.isSavingCodeforcesHandle.set(false);
        this.codeforcesHandleInput.set(resp?.codeforcesHandle || '');
        this.planStatus.set({
          success: true,
          message: handle ? `Codeforces handle linked: ${resp?.codeforcesHandle || handle}` : 'Codeforces handle removed.'
        });
        this.loadIntegrationsStatus();
      },
      error: (err: HttpErrorResponse) => {
        this.isSavingCodeforcesHandle.set(false);
        this.planStatus.set({ success: false, message: this.extractError(err, 'Failed to save Codeforces handle.') });
      }
    });
  }

  syncCodeforcesAnalytics(windowDays: number = 30) {
    if (this.isSyncingCodeforcesAnalytics()) {
      return;
    }
    if (!this.codeforcesConnected()) {
      this.planStatus.set({ success: false, message: 'Set your Codeforces handle before syncing analytics.' });
      return;
    }

    this.isSyncingCodeforcesAnalytics.set(true);
    const payload: CodeforcesAnalyticsSyncRequest = { windowDays };
    this.http.post<CodeforcesAnalytics>('/api/rishi/integrations/codeforces/analytics/sync', payload).subscribe({
      next: (resp) => {
        this.isSyncingCodeforcesAnalytics.set(false);
        this.codeforcesAnalytics.set(resp || null);
        this.planStatus.set({
          success: true,
          message: `Codeforces analytics synced (${resp?.windowDays || windowDays}d): rating ${resp?.currentRating || 0}, solved ${resp?.solvedCurrentWindow || 0}.`
        });
        this.loadIntegrationsStatus();
      },
      error: (err: HttpErrorResponse) => {
        this.isSyncingCodeforcesAnalytics.set(false);
        this.planStatus.set({ success: false, message: this.extractError(err, 'Codeforces analytics sync failed.') });
      }
    });
  }

  saveTogglToken() {
    if (this.isSavingTogglToken()) {
      return;
    }
    const apiToken = this.togglTokenInput().trim();
    if (!apiToken) {
      this.planStatus.set({ success: false, message: 'Paste your Toggl API token before saving.' });
      return;
    }

    this.isSavingTogglToken.set(true);
    this.http.post('/api/rishi/integrations/toggl/token', { apiToken }).subscribe({
      next: () => {
        this.isSavingTogglToken.set(false);
        this.togglTokenInput.set('');
        this.planStatus.set({ success: true, message: 'Toggl connected successfully.' });
        this.loadIntegrationsStatus();
      },
      error: (err: HttpErrorResponse) => {
        this.isSavingTogglToken.set(false);
        this.planStatus.set({ success: false, message: this.extractError(err, 'Failed to connect Toggl.') });
      }
    });
  }

  syncTogglFocus(windowDays: number = 7) {
    if (this.isSyncingTogglFocus()) {
      return;
    }
    if (!this.togglConnected()) {
      this.planStatus.set({ success: false, message: 'Connect Toggl before syncing focus time.' });
      return;
    }

    this.isSyncingTogglFocus.set(true);
    const payload: TogglFocusSyncRequest = { windowDays };
    this.http.post<TogglFocus>('/api/rishi/integrations/toggl/focus/sync', payload).subscribe({
      next: (resp) => {
        this.isSyncingTogglFocus.set(false);
        this.togglFocus.set(resp || null);
        this.planStatus.set({
          success: true,
          message: `Toggl synced (${resp?.windowDays || windowDays}d): ${resp?.trackedMinutes || 0} tracked minutes.`
        });
        this.loadIntegrationsStatus();
      },
      error: (err: HttpErrorResponse) => {
        this.isSyncingTogglFocus.set(false);
        this.planStatus.set({ success: false, message: this.extractError(err, 'Toggl sync failed.') });
      }
    });
  }

  disconnectToggl() {
    this.http.post('/api/rishi/integrations/toggl/disconnect', {}).subscribe({
      next: () => {
        this.planStatus.set({ success: true, message: 'Toggl disconnected.' });
        this.loadIntegrationsStatus();
      },
      error: (err: HttpErrorResponse) => {
        this.planStatus.set({ success: false, message: this.extractError(err, 'Failed to disconnect Toggl.') });
      }
    });
  }

  markTaskDone(taskId: number) {
    this.http.post(`/api/rishi/integrations/tasks/${taskId}/done`, {}).subscribe({
      next: () => this.loadIntegrationsStatus(),
      error: () => {}
    });
  }

  private handleOAuthCallback() {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');
    const state = params.get('state');
    const error = params.get('error');

    if (error) {
      this.planStatus.set({ success: false, message: `OAuth failed: ${error}` });
      this.clearOAuthQueryParams();
      return;
    }

    if (!code || !state) {
      return;
    }

    const redirectUri = this.getCurrentPageWithoutQuery();
    this.http.post<OAuthCompleteResponse>('/api/rishi/integrations/oauth/complete', {
      code,
      state,
      redirectUri
    }).subscribe({
      next: () => {
        this.planStatus.set({ success: true, message: 'Integration connected successfully.' });
        this.loadIntegrationsStatus();
        this.clearOAuthQueryParams();
      },
      error: (err: HttpErrorResponse) => {
        this.planStatus.set({ success: false, message: this.extractError(err, 'OAuth completion failed.') });
        this.clearOAuthQueryParams();
      }
    });
  }

  private startOAuthFlow(url: string) {
    if (!isPlatformBrowser(this.platformId) || this.isConnectingIntegration()) {
      return;
    }
    this.isConnectingIntegration.set(true);
    const redirectUri = this.getCurrentPageWithoutQuery();
    this.http.get<OAuthUrlResponse>(`${url}?redirectUri=${encodeURIComponent(redirectUri)}`).subscribe({
      next: (resp) => {
        this.isConnectingIntegration.set(false);
        if (resp?.authUrl) {
          window.location.href = resp.authUrl;
        }
      },
      error: (err: HttpErrorResponse) => {
        this.isConnectingIntegration.set(false);
        this.planStatus.set({ success: false, message: this.extractError(err, 'Failed to start OAuth flow.') });
      }
    });
  }

  private getCurrentPageWithoutQuery(): string {
    if (!isPlatformBrowser(this.platformId)) {
      return '';
    }
    return `${window.location.origin}${window.location.pathname}`;
  }

  private clearOAuthQueryParams() {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    window.history.replaceState({}, document.title, this.getCurrentPageWithoutQuery());
  }

  loadStudyPlan() {
    this.http.get<StudyPlanResponse>('/api/advice/me/study-plan').subscribe({
      next: (resp) => this.applyStudyPlan(resp),
      error: () => this.hasStudyPlan.set(false)
    });
  }

  onModelChange(value: string) {
    this.saveConfigInternal({ onSuccess: () => {}, onError: () => {} });
  }

  updateApiKey(event: Event) {
    const input = event.target as HTMLInputElement;
    this.apiKeyInput.set(input.value);
  }

  saveConfig() {
    const hasNewKey = !!this.apiKeyInput().trim();
    if (!hasNewKey && !this.hasApiKey()) {
      this.configStatus.set({ success: false, message: 'Paste API key before saving.' });
      return;
    }

    this.saveConfigInternal({
      onSuccess: () => {
        this.configStatus.set({ success: true, message: 'Neural config updated.' });
        this.apiKeyInput.set('');
      },
      onError: (message) => this.configStatus.set({ success: false, message })
    });
  }

  sendMessage() {
    const text = this.currentInput.trim();
    if (!text || this.isGenerating()) return;

    this.addUserMessage(text, 'text');
    this.currentInput = '';
    this.scrollToBottom();
    if (this.mode() === 'agent') {
      this.askAgent(text, 'text');
    } else {
      this.askRishi(text, 'text');
    }
  }

  autoGrow(event: any) {
    const el = event.target;
    el.style.height = 'auto';
    el.style.height = el.scrollHeight + 'px';
  }

  generateStrategy() {
    if (this.isGenerating()) return;
    const prompt = 'Analyze my current Bloom Level and performance metrics. Create a short-term tactical optimization plan for my next study session.';
    this.addUserMessage('Rishi, synthesize a tactical optimization plan.', 'text');
    this.scrollToBottom();
    if (this.mode() === 'agent') {
      this.askAgent(prompt, 'strategy');
    } else {
      this.askRishi(prompt, 'strategy');
    }
  }

  private askRishi(message: string, responseType: 'text' | 'strategy') {
    if (!this.hasApiKey() && !this.apiKeyInput().trim()) {
      this.addAssistantMessage('Attach your neural access key (Mistral API Key) in settings to initialize the interface.', 'text');
      this.scrollToBottom();
      return;
    }

    this.isGenerating.set(true);
    
    const payload: any = {
      message,
      model: this.model(),
      type: responseType
    };
    
    if (this.activeThreadId()) {
      payload.threadId = this.activeThreadId();
    }

    this.http.post<LearnResponse>('/api/advice/me/learn', payload).subscribe({
      next: (resp) => {
        this.addAssistantMessage(resp.reply || 'Interface timeout.', responseType);
        
        if (resp.threadId) {
          this.activeThreadId.set(resp.threadId);
          this.loadThreads(); // Refresh sidebar to show the newly created thread
        }
        
        this.isGenerating.set(false);
        this.scrollToBottom();
      },
      error: (err: HttpErrorResponse) => {
        this.addAssistantMessage(this.extractError(err, 'Neural link failure. Retrying...'), 'text');
        this.isGenerating.set(false);
        this.scrollToBottom();
      }
    });
  }

  private askAgent(message: string, responseType: 'text' | 'strategy') {
    if (!this.hasApiKey() && !this.apiKeyInput().trim()) {
      this.addAssistantMessage('Attach your neural access key (Mistral API Key) in settings to initialize the interface.', 'text');
      this.scrollToBottom();
      return;
    }

    this.isGenerating.set(true);
    const payload: any = {
      message,
      model: this.model(),
      threadId: this.activeThreadId(),
      autoSchedule: message.toLowerCase().includes('schedule') || message.toLowerCase().includes('calendar')
    };

    this.http.post<AgentTaskEnqueueResponse>('/api/rishi/agent/execute', payload).subscribe({
      next: (enqueueResp) => {
        if (!enqueueResp?.taskId) {
          this.addAssistantMessage('Agent queue failed: no task ID returned.', 'text');
          this.isGenerating.set(false);
          this.scrollToBottom();
          return;
        }
        this.pollAgentTask(enqueueResp.taskId, responseType);
      },
      error: (err: HttpErrorResponse) => {
        this.addAssistantMessage(this.extractError(err, 'Agent mode failed. Retry in chat mode.'), 'text');
        this.isGenerating.set(false);
        this.scrollToBottom();
      }
    });
  }

  private pollAgentTask(taskId: string, responseType: 'text' | 'strategy', attempt: number = 0) {
    const maxAttempts = 90; // 90 * 2s = 3 min max wait
    const pollIntervalMs = 2000;

    if (attempt >= maxAttempts) {
      this.addAssistantMessage('Agent request timed out. The server is under heavy load. Try again shortly.', 'text');
      this.isGenerating.set(false);
      this.scrollToBottom();
      return;
    }

    setTimeout(() => {
      this.http.get<AgentTaskPollResponse>(`/api/rishi/agent/task/${taskId}`).subscribe({
        next: (poll) => {
          if (poll.status === 'COMPLETED' && poll.response) {
            const resp = poll.response;
            const actions = (resp.actions || []).map((item) => `- ${item}`).join('\n');
            const content = actions ? `${resp.reply}\n\n**Agent Actions**\n${actions}` : resp.reply;
            this.addAssistantMessage(content || 'Agent returned no response.', responseType);

            if (resp.threadId) {
              this.activeThreadId.set(resp.threadId);
              this.loadThreads();
            }

            this.mode.set('agent');
            this.loadIntegrationsStatus();
            this.isGenerating.set(false);
            this.scrollToBottom();
          } else if (poll.status === 'FAILED') {
            this.addAssistantMessage(poll.error || 'Agent processing failed.', 'text');
            this.isGenerating.set(false);
            this.scrollToBottom();
          } else {
            // PENDING or PROCESSING — keep polling
            this.pollAgentTask(taskId, responseType, attempt + 1);
          }
        },
        error: () => {
          // Network hiccup, retry polling
          this.pollAgentTask(taskId, responseType, attempt + 1);
        }
      });
    }, pollIntervalMs);
  }

  private saveConfigInternal(options: { onSuccess: () => void; onError: (message: string) => void }) {
    this.isSavingConfig.set(true);
    const payload: any = {
      provider: this.provider(),
      model: this.model()
    };
    if (this.apiKeyInput().trim()) {
      payload.apiKey = this.apiKeyInput().trim();
    }

    this.http.post<GenAiConfigResponse>('/api/advice/me/genai-config', payload).subscribe({
      next: (resp) => {
        this.provider.set(resp.provider || 'mistral');
        this.model.set(resp.model || 'open-mixtral-8x7b');
        this.hasApiKey.set(!!resp.hasApiKey);
        this.maskedApiKey.set(resp.maskedApiKey || '');
        this.isSavingConfig.set(false);
        this.updateStatus();
        options.onSuccess();
      },
      error: (err: HttpErrorResponse) => {
        this.isSavingConfig.set(false);
        options.onError(this.extractError(err, 'Sync failure.'));
      }
    });
  }

  private applyStudyPlan(resp: StudyPlanResponse) {
    const hasPlan = !!resp?.hasPlan && !!resp?.plan;
    this.hasStudyPlan.set(hasPlan);
    this.studyPlanText.set(hasPlan ? (resp.plan || '') : '');
    this.studyPlanTopic.set(resp?.topic || '');
    this.studyPlanDuration.set(resp?.durationDays || 0);
    this.studyPlanGeneratedAt.set(resp?.generatedAt || '');
  }

  private addUserMessage(content: string, type: 'text' | 'strategy') {
    this.messages.update((msgs) => [
      ...msgs,
      { id: Date.now().toString(), role: 'user', content, type }
    ]);
  }

  private addAssistantMessage(content: string, type: 'text' | 'strategy' | 'init') {
    this.messages.update((msgs) => [
      ...msgs,
      { id: (Date.now() + 1).toString(), role: 'assistant', content, type }
    ]);
  }

  private normalizeRole(role: string): 'user' | 'assistant' {
    return role?.toLowerCase() === 'user' ? 'user' : 'assistant';
  }

  private normalizeType(type: string): 'text' | 'strategy' | 'init' {
    if (type === 'strategy') return 'strategy';
    if (type === 'init') return 'init';
    return 'text';
  }

  formatAttemptSource(source: string): string {
    if (!source) {
      return 'Unknown';
    }
    return source
      .toLowerCase()
      .split('_')
      .map((chunk) => (chunk ? chunk.charAt(0).toUpperCase() + chunk.slice(1) : chunk))
      .join(' ');
  }

  toggleSettingsPanel() { this.showSettings.set(!this.showSettings()); }
  togglePlanExpansion() { this.isPlanExpanded.set(!this.isPlanExpanded()); }

  private resetMessages() {
    this.messages.set([
      { id: '1', role: 'assistant', content: this.greeting, type: 'init' }
    ]);
  }

  private extractError(error: HttpErrorResponse, fallback: string): string {
    return error?.error?.error || error?.error?.message || fallback;
  }

  private updateStatus() {
    if (this.hasApiKey()) {
      this.statusText.set(`AI Status: ${this.provider().toUpperCase()} linked (${this.model()})`);
    } else {
      this.statusText.set('AI Status: Key required');
    }
  }

  private scrollToBottom() {
    setTimeout(() => {
      const container = document.querySelector('.advisor-chat-scroll');
      if (container) container.scrollTop = container.scrollHeight;
    }, 100);
  }

  private getAttemptHistoryLimit(days: number): number {
    if (days <= 7) {
      return 12;
    }
    if (days <= 14) {
      return 20;
    }
    return 30;
  }

  toPercent(value: number): number {
    if (!Number.isFinite(value)) {
      return 0;
    }
    return Math.max(0, Math.min(100, value));
  }
}

