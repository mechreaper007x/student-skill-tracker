import { CommonModule, isPlatformBrowser } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, PLATFORM_ID, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';

interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  type: 'text' | 'strategy' | 'init';
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
  memoryCount?: number;
}

interface MemoryMessage {
  role: string;
  content: string;
  type: string;
  timestamp: string;
}

interface MemoryResponse {
  messages: MemoryMessage[];
  count: number;
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

@Component({
  selector: 'app-advisor',
  standalone: true,
  imports: [CommonModule, LucideAngularModule, FormsModule],
  template: `
    <div class="h-[calc(100vh-6rem)] p-6 animate-fade-in flex flex-col gap-6">
      <div class="flex-1 min-h-0 flex flex-col noir-card overflow-hidden">
        <div class="p-6 border-b border-noir-800 bg-noir-900/50 flex items-center justify-between gap-4">
          <div class="flex items-center gap-4 min-w-0">
            <div class="p-3 rounded-2xl bg-crimson-600/10 border border-crimson-600/20 text-crimson-500 shadow-lg shadow-crimson-900/10 mb-[-4px]">
              <lucide-icon name="Brain" class="w-8 h-8"></lucide-icon>
            </div>
            <div>
              <h2 class="text-xl font-bold text-white uppercase italic tracking-tighter">The Rishi Interface</h2>
              <div class="flex items-center gap-2">
                <span class="w-2 h-2 rounded-full animate-pulse"
                  [class.bg-crimson-600]="!hasApiKey()"
                  [class.bg-emerald-500]="hasApiKey() && !isGenerating() && !isGeneratingPlan()"
                  [class.bg-amber-500]="isGenerating() || isGeneratingPlan()"
                ></span>
                <p class="text-[10px] font-bold text-noir-500 uppercase tracking-widest">{{ statusText() }}</p>
              </div>
            </div>
          </div>

          <button
            (click)="toggleSettingsPanel()"
            class="shrink-0 flex items-center gap-2 rounded-xl border border-noir-700 bg-noir-900 px-3 py-2 text-noir-300 hover:text-white hover:border-crimson-500/40 transition-colors"
            title="Rishi Settings"
          >
            <lucide-icon name="Settings" class="w-4 h-4 transition-transform" [class.rotate-90]="showSettings()"></lucide-icon>
            <span class="hidden sm:inline text-[10px] font-bold uppercase tracking-widest">{{ showSettings() ? 'Hide' : 'Settings' }}</span>
          </button>
        </div>

        @if (showSettings()) {
        <div class="p-6 border-b border-noir-800 bg-noir-950/50 space-y-4 animate-fade-in">
          <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label class="block text-[10px] font-bold uppercase tracking-widest text-noir-500 mb-2">Provider</label>
              <input
                type="text"
                [value]="provider()"
                disabled
                class="w-full bg-black border border-noir-800 rounded-xl py-2.5 px-3 text-noir-400 font-mono text-xs"
              />
            </div>

            <div>
              <label class="block text-[10px] font-bold uppercase tracking-widest text-noir-500 mb-2">Model</label>
              <input
                type="text"
                [value]="model()"
                (input)="updateModel($event)"
                class="w-full bg-black border border-noir-700 rounded-xl py-2.5 px-3 text-noir-100 font-mono text-xs focus:outline-none focus:border-crimson-500/60"
              />
            </div>

            <div>
              <label class="block text-[10px] font-bold uppercase tracking-widest text-noir-500 mb-2">Mixtral API Key</label>
              <div class="flex items-center gap-2">
                <input
                  type="password"
                  [value]="apiKeyInput()"
                  (input)="updateApiKey($event)"
                  [placeholder]="hasApiKey() ? ('Saved: ' + maskedApiKey() + ' | enter new key to rotate') : 'Paste key and save'"
                  class="w-full bg-black border border-noir-700 rounded-xl py-2.5 px-3 text-noir-100 font-mono text-xs focus:outline-none focus:border-crimson-500/60"
                />
                <button
                  (click)="saveConfig()"
                  [disabled]="isSavingConfig()"
                  class="px-4 py-2.5 rounded-xl bg-noir-800 border border-noir-700 text-noir-200 text-[10px] font-black uppercase tracking-widest hover:border-crimson-500/50 hover:text-white disabled:opacity-50 whitespace-nowrap"
                >
                  {{ isSavingConfig() ? 'Saving...' : 'Save' }}
                </button>
              </div>
            </div>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-4 gap-3">
            <div class="md:col-span-2">
              <label class="block text-[10px] font-bold uppercase tracking-widest text-noir-500 mb-2">Study Plan Topic</label>
              <input
                [(ngModel)]="planTopic"
                type="text"
                placeholder="e.g. Dynamic Programming"
                class="w-full bg-black border border-noir-700 rounded-xl py-2.5 px-3 text-noir-100 font-mono text-xs focus:outline-none focus:border-crimson-500/60"
              />
            </div>

            <div>
              <label class="block text-[10px] font-bold uppercase tracking-widest text-noir-500 mb-2">Duration (days)</label>
              <input
                [(ngModel)]="planDurationDays"
                type="number"
                min="1"
                max="60"
                class="w-full bg-black border border-noir-700 rounded-xl py-2.5 px-3 text-noir-100 font-mono text-xs focus:outline-none focus:border-crimson-500/60"
              />
            </div>

            <div class="flex items-end gap-2">
              <button
                (click)="generateStudyPlan()"
                [disabled]="isGeneratingPlan()"
                class="w-full px-4 py-2.5 rounded-xl bg-crimson-700/90 border border-crimson-600/60 text-white text-[10px] font-black uppercase tracking-widest hover:bg-crimson-600 disabled:opacity-50"
              >
                {{ isGeneratingPlan() ? 'Generating...' : 'Generate Plan' }}
              </button>
              <button
                (click)="clearMemory()"
                [disabled]="isClearingMemory()"
                class="px-4 py-2.5 rounded-xl bg-noir-800 border border-noir-700 text-noir-200 text-[10px] font-black uppercase tracking-widest hover:border-crimson-500/50 hover:text-white disabled:opacity-50 whitespace-nowrap"
              >
                {{ isClearingMemory() ? 'Clearing...' : 'Clear Memory' }}
              </button>
            </div>
          </div>

          <div>
            <label class="block text-[10px] font-bold uppercase tracking-widest text-noir-500 mb-2">Study Plan Goals (optional)</label>
            <textarea
              [(ngModel)]="planGoals"
              rows="2"
              placeholder="e.g. Crack medium DP + complete one project"
              class="w-full bg-black border border-noir-700 rounded-xl py-2.5 px-3 text-noir-100 font-mono text-xs focus:outline-none focus:border-crimson-500/60 resize-none"
            ></textarea>
          </div>

          @if (configStatus()) {
            <div class="text-[10px] font-bold uppercase tracking-widest"
              [class.text-emerald-500]="configStatus()!.success"
              [class.text-crimson-500]="!configStatus()!.success"
            >
              {{ configStatus()!.message }}
            </div>
          }

          @if (planStatus()) {
            <div class="text-[10px] font-bold uppercase tracking-widest"
              [class.text-emerald-500]="planStatus()!.success"
              [class.text-crimson-500]="!planStatus()!.success"
            >
              {{ planStatus()!.message }}
            </div>
          }

          @if (hasStudyPlan()) {
            <div class="rounded-xl border border-crimson-500/20 bg-black/40 p-4 space-y-2">
              <div class="flex flex-wrap items-center gap-3 text-[10px] uppercase tracking-widest font-bold">
                <span class="text-crimson-400">{{ studyPlanTopic() || 'Untitled Plan' }}</span>
                <span class="text-noir-500">{{ studyPlanDuration() }} Days</span>
                @if (studyPlanGeneratedAt()) {
                  <span class="text-noir-600">Generated: {{ studyPlanGeneratedAt() | date:'medium' }}</span>
                }
                <button
                  (click)="togglePlanExpansion()"
                  class="ml-auto px-2 py-1 rounded border border-noir-700 text-noir-300 hover:text-white hover:border-crimson-500/40"
                >
                  {{ isPlanExpanded() ? 'Collapse' : 'Expand' }}
                </button>
              </div>
              <div
                class="overflow-y-auto custom-scrollbar rounded border border-noir-800 bg-black/50 p-3"
                [class.max-h-56]="!isPlanExpanded()"
              >
                <pre class="text-xs text-noir-200 font-mono whitespace-pre-wrap leading-relaxed">{{ studyPlanText() }}</pre>
              </div>
            </div>
          }
        </div>
        }

        <div class="advisor-chat-scroll flex-1 min-h-0 overflow-y-auto p-8 space-y-8 scroll-smooth scrollbar-thin scrollbar-thumb-noir-800">
          @for (msg of messages(); track msg.id) {
            <div class="flex gap-6" [class.flex-row-reverse]="msg.role === 'user'">
              <div class="w-10 h-10 rounded-xl flex items-center justify-center shrink-0 border border-noir-800 shadow-sm"
                [class.bg-noir-800]="msg.role === 'user'"
                [class.bg-crimson-600]="msg.role === 'assistant'"
                [class.text-white]="msg.role === 'assistant'"
              >
                @if (msg.role === 'user') {
                  <span class="text-xs font-bold text-noir-300">YO</span>
                } @else {
                  <lucide-icon name="Bot" class="w-5 h-5"></lucide-icon>
                }
              </div>

              <div class="max-w-[75%] rounded-2xl p-5 text-sm leading-relaxed relative group transition-all"
                [class.bg-noir-900]="msg.role === 'user'"
                [class.text-noir-200]="msg.role === 'user'"
                [class.border-noir-800]="msg.role === 'user'"
                [class.bg-noir-800/40]="msg.role === 'assistant'"
                [class.border]="true"
                [class.border-crimson-500/20]="msg.role === 'assistant'"
                [class.text-noir-100]="msg.role === 'assistant'"
                [class.shadow-lg]="msg.type === 'strategy'"
              >
                <div class="font-mono whitespace-pre-wrap">{{ msg.content }}</div>

                @if (msg.type === 'strategy') {
                  <div class="absolute inset-0 bg-crimson-500/5 rounded-2xl animate-pulse-slow pointer-events-none border border-crimson-500/30"></div>
                }
              </div>
            </div>
          }

          @if (isGenerating() || isGeneratingPlan()) {
            <div class="flex gap-6">
              <div class="w-10 h-10 rounded-xl flex items-center justify-center shrink-0 border border-crimson-500/20 bg-crimson-600 text-white">
                <lucide-icon name="Bot" class="w-5 h-5"></lucide-icon>
              </div>
              <div class="max-w-[75%] rounded-2xl p-5 text-sm border border-crimson-500/20 bg-noir-800/40 text-noir-200 font-mono animate-pulse">
                {{ isGeneratingPlan() ? 'Rishi is forging your study plan...' : 'Rishi is synthesizing your learning path...' }}
              </div>
            </div>
          }
        </div>

        <div class="p-6 border-t border-noir-800 bg-noir-900/50">
          <div class="relative flex items-center gap-4 max-w-4xl mx-auto">
            <button
              (click)="generateStrategy()"
              [disabled]="isGenerating() || isGeneratingPlan()"
              class="p-3 rounded-xl text-crimson-500 hover:bg-crimson-500/10 border border-noir-800 hover:border-crimson-500/30 transition-all group bg-noir-900 disabled:opacity-40"
              title="Quick Strategy Prompt"
            >
              <lucide-icon name="Sparkles" class="w-6 h-6 group-hover:rotate-12 transition-transform"></lucide-icon>
            </button>

            <div class="relative flex-1">
              <input
                [(ngModel)]="currentInput"
                (keyup.enter)="sendMessage()"
                type="text"
                placeholder="Ask Rishi to teach any topic..."
                [disabled]="isGenerating() || isGeneratingPlan()"
                class="w-full bg-noir-900 border border-noir-800 rounded-2xl py-4 px-6 text-noir-100 placeholder:text-noir-600 focus:outline-none focus:border-crimson-500/50 focus:ring-1 focus:ring-crimson-500/50 transition-all font-mono text-sm shadow-inner disabled:opacity-50"
              >

              <button
                (click)="sendMessage()"
                [disabled]="isGenerating() || isGeneratingPlan() || !currentInput.trim()"
                class="absolute right-2 top-1/2 -translate-y-1/2 p-2.5 rounded-xl bg-crimson-600 text-white shadow-lg shadow-crimson-900/20 hover:bg-crimson-500 disabled:opacity-30 disabled:grayscale transition-all"
              >
                <lucide-icon name="Send" class="w-5 h-5"></lucide-icon>
              </button>
            </div>
          </div>
          <div class="mt-4 text-center">
            <span class="text-[9px] text-noir-600 uppercase tracking-[0.3em] font-bold">Memory-aware tutoring | Persistent study plans | Mixtral runtime</span>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; height: 100%; background: transparent; }
  `]
})
export class AdvisorComponent implements OnInit {
  private http = inject(HttpClient);
  private platformId = inject(PLATFORM_ID);

  private readonly greeting = 'Greetings, seeker. Attach your Mixtral API key above, then ask any topic and I will teach it with examples and exercises.';

  messages = signal<Message[]>([]);
  currentInput = '';

  provider = signal('mistral');
  model = signal('open-mixtral-8x7b');
  apiKeyInput = signal('');
  maskedApiKey = signal('');
  hasApiKey = signal(false);

  isSavingConfig = signal(false);
  isGenerating = signal(false);
  isGeneratingPlan = signal(false);
  isClearingMemory = signal(false);

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
  }

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    this.loadConfig();
    this.loadMemory();
    this.loadStudyPlan();
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

  loadMemory() {
    this.http.get<MemoryResponse>('/api/advice/me/memory').subscribe({
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
      error: () => this.resetMessages()
    });
  }

  loadStudyPlan() {
    this.http.get<StudyPlanResponse>('/api/advice/me/study-plan').subscribe({
      next: (resp) => this.applyStudyPlan(resp),
      error: () => {
        this.hasStudyPlan.set(false);
      }
    });
  }

  updateModel(event: Event) {
    const input = event.target as HTMLInputElement;
    this.model.set(input.value);
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
        this.configStatus.set({ success: true, message: 'Mixtral config saved.' });
      },
      onError: (message) => {
        this.configStatus.set({ success: false, message });
      }
    });
  }

  clearMemory() {
    this.isClearingMemory.set(true);
    this.http.post<{ message: string }>('/api/advice/me/memory/clear', {}).subscribe({
      next: () => {
        this.resetMessages();
        this.isClearingMemory.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.isClearingMemory.set(false);
        this.planStatus.set({ success: false, message: this.extractError(err, 'Failed to clear memory.') });
      }
    });
  }

  sendMessage() {
    const text = this.currentInput.trim();
    if (!text || this.isGenerating() || this.isGeneratingPlan()) return;

    this.addUserMessage(text, 'text');
    this.currentInput = '';
    this.scrollToBottom();
    this.askRishi(text, 'text');
  }

  generateStrategy() {
    if (this.isGenerating() || this.isGeneratingPlan()) return;

    const prompt = 'Create a short tactical strategy for today based on DSA learning momentum, including one easy, one medium, and one revision task.';
    this.addUserMessage('Rishi, give me a tactical plan for today.', 'text');
    this.scrollToBottom();
    this.askRishi(prompt, 'strategy');
  }

  generateStudyPlan() {
    if (this.isGenerating() || this.isGeneratingPlan()) return;

    const topic = this.planTopic.trim();
    if (!topic) {
      this.planStatus.set({ success: false, message: 'Study plan topic is required.' });
      return;
    }

    const duration = this.sanitizeDuration(this.planDurationDays);
    this.planDurationDays = duration;

    if (!this.hasApiKey() && !this.apiKeyInput().trim()) {
      this.planStatus.set({ success: false, message: 'Attach your Mixtral API key before generating a study plan.' });
      return;
    }

    if (this.apiKeyInput().trim()) {
      this.saveConfigInternal({
        onSuccess: () => {
          this.configStatus.set({ success: true, message: 'Mixtral config saved.' });
          this.requestStudyPlan(topic, duration);
        },
        onError: (message) => {
          this.planStatus.set({ success: false, message });
        }
      });
      return;
    }

    this.requestStudyPlan(topic, duration);
  }

  private askRishi(message: string, responseType: 'text' | 'strategy') {
    if (!this.hasApiKey() && !this.apiKeyInput().trim()) {
      this.addAssistantMessage('Attach your Mixtral API key above and click Save, then ask again.', 'text');
      this.scrollToBottom();
      return;
    }

    if (this.apiKeyInput().trim()) {
      this.saveConfigInternal({
        onSuccess: () => {
          this.configStatus.set({ success: true, message: 'Mixtral config saved.' });
          this.requestLearningResponse(message, responseType);
        },
        onError: (errMessage) => {
          this.addAssistantMessage(errMessage, 'text');
          this.scrollToBottom();
        }
      });
      return;
    }

    this.requestLearningResponse(message, responseType);
  }

  private requestLearningResponse(message: string, responseType: 'text' | 'strategy') {
    this.isGenerating.set(true);
    this.updateStatus();

    this.http.post<LearnResponse>('/api/advice/me/learn', {
      message,
      model: this.model(),
      type: responseType
    }).subscribe({
      next: (resp) => {
        this.addAssistantMessage(resp.reply || 'No response from model.', responseType);
        this.isGenerating.set(false);
        this.updateStatus();
        this.scrollToBottom();
      },
      error: (err: HttpErrorResponse) => {
        this.addAssistantMessage(this.extractError(err, 'I could not reach Mixtral right now. Try again.'), 'text');
        this.isGenerating.set(false);
        this.updateStatus();
        this.scrollToBottom();
      }
    });
  }

  private requestStudyPlan(topic: string, durationDays: number) {
    this.isGeneratingPlan.set(true);
    this.planStatus.set(null);
    this.updateStatus();

    this.http.post<StudyPlanResponse>('/api/advice/me/study-plan', {
      topic,
      goals: this.planGoals.trim(),
      durationDays,
      model: this.model()
    }).subscribe({
      next: (resp) => {
        this.applyStudyPlan(resp);
        this.addAssistantMessage(`Study plan generated for "${topic}" (${durationDays} days). Scroll up to review it.`, 'strategy');
        this.planStatus.set({ success: true, message: 'Study plan generated and saved to your Rishi memory.' });
        this.isGeneratingPlan.set(false);
        this.updateStatus();
        this.scrollToBottom();
      },
      error: (err: HttpErrorResponse) => {
        this.planStatus.set({ success: false, message: this.extractError(err, 'Failed to generate study plan.') });
        this.isGeneratingPlan.set(false);
        this.updateStatus();
      }
    });
  }

  private saveConfigInternal(options: { onSuccess: () => void; onError: (message: string) => void }) {
    const hasNewKey = !!this.apiKeyInput().trim();
    if (!hasNewKey && !this.hasApiKey()) {
      options.onError('Paste API key before saving.');
      return;
    }

    this.isSavingConfig.set(true);
    const payload: { provider: string; model: string; apiKey?: string } = {
      provider: this.provider(),
      model: this.model()
    };
    if (hasNewKey) {
      payload.apiKey = this.apiKeyInput().trim();
    }

    this.http.post<GenAiConfigResponse>('/api/advice/me/genai-config', payload).subscribe({
      next: (resp) => {
        this.provider.set(resp.provider || 'mistral');
        this.model.set(resp.model || 'open-mixtral-8x7b');
        this.hasApiKey.set(!!resp.hasApiKey);
        this.maskedApiKey.set(resp.maskedApiKey || '');
        this.apiKeyInput.set('');
        this.isSavingConfig.set(false);
        this.updateStatus();
        options.onSuccess();
      },
      error: (err: HttpErrorResponse) => {
        this.isSavingConfig.set(false);
        options.onError(this.extractError(err, 'Failed to save config.'));
      }
    });
  }

  private applyStudyPlan(resp: StudyPlanResponse) {
    const hasPlan = !!resp?.hasPlan && !!resp?.plan;
    this.hasStudyPlan.set(hasPlan);
    this.isPlanExpanded.set(false);
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

  private sanitizeDuration(value: number): number {
    if (!Number.isFinite(value) || value < 1) return 7;
    return Math.min(Math.round(value), 60);
  }

  togglePlanExpansion() {
    this.isPlanExpanded.set(!this.isPlanExpanded());
  }

  toggleSettingsPanel() {
    this.showSettings.set(!this.showSettings());
  }

  private resetMessages() {
    this.messages.set([
      {
        id: '1',
        role: 'assistant',
        content: this.greeting,
        type: 'init'
      }
    ]);
  }

  private extractError(error: HttpErrorResponse, fallback: string): string {
    return error?.error?.error || error?.error?.message || fallback;
  }

  private updateStatus() {
    if (this.isGeneratingPlan()) {
      this.statusText.set('AI Status: Mixtral is generating your study plan');
      return;
    }
    if (this.isGenerating()) {
      this.statusText.set('AI Status: Mixtral is generating response');
      return;
    }
    if (this.hasApiKey()) {
      this.statusText.set(`AI Status: ${this.provider().toUpperCase()} linked (${this.model()})`);
      return;
    }
    this.statusText.set('AI Status: Key required');
  }

  private scrollToBottom() {
    setTimeout(() => {
      const container = document.querySelector('.advisor-chat-scroll');
      if (container) container.scrollTop = container.scrollHeight;
    }, 100);
  }
}
