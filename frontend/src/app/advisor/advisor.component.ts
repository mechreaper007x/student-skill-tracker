import { CommonModule, isPlatformBrowser } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, PLATFORM_ID, Pipe, PipeTransform, inject, signal, computed, effect } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { LucideAngularModule } from 'lucide-angular';
import mermaid from 'mermaid';

declare global {
  interface Window {
    SpeechRecognition?: new () => SpeechRecognition;
    webkitSpeechRecognition?: new () => SpeechRecognition;
  }
}

interface SpeechRecognition extends EventTarget {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  onresult: ((event: AppSpeechRecognitionEvent) => void) | null;
  onerror: ((event: AppSpeechRecognitionErrorEvent) => void) | null;
  onend: (() => void) | null;
  start(): void;
  stop(): void;
}

interface AppSpeechRecognitionEvent {
  results: SpeechRecognitionResultList;
}

interface AppSpeechRecognitionErrorEvent {
  error: string;
}

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
        <header class="h-20 border-b border-noir-800 bg-noir-950/80 backdrop-blur-md flex items-center justify-between px-8 z-20">
          <div class="flex items-center gap-4">
            <div class="p-2.5 rounded-xl bg-crimson-600 text-white shadow-lg shadow-crimson-900/20">
              <lucide-icon name="Brain" class="w-6 h-6"></lucide-icon>
            </div>
            <div>
              <h1 class="text-lg font-black tracking-tighter text-white uppercase italic">RISHI_v4.2</h1>
              <p class="text-[9px] text-noir-500 font-mono tracking-widest">MISTRAL_AI_RUNTIME // NEURAL_ADVISOR</p>
            </div>
          </div>

          <div class="flex items-center gap-3">
            <button (click)="startMockInterview()" class="flex items-center gap-2 px-4 py-2 text-[10px] font-black uppercase tracking-widest border border-emerald-500/50 text-emerald-400 bg-noir-900 hover:bg-emerald-600/10 hover:text-emerald-300 hover:border-emerald-400 transition-all rounded-xl">
              <lucide-icon name="Mic" class="w-3.5 h-3.5"></lucide-icon>
              MOCK INTERVIEW
            </button>
            <button
              (click)="toggleVoiceInterview()"
              class="flex items-center gap-2 px-4 py-2 text-[10px] font-black uppercase tracking-widest border transition-all rounded-xl disabled:opacity-40"
              [class.border-cyan-500/50]="voiceInterviewMode()"
              [class.text-cyan-300]="voiceInterviewMode()"
              [class.bg-cyan-600/10]="voiceInterviewMode()"
              [class.border-noir-700]="!voiceInterviewMode()"
              [class.text-noir-300]="!voiceInterviewMode()"
              [disabled]="!voiceSupported()"
              title="Enable sound-based interview interaction"
            >
              <lucide-icon [name]="voiceInterviewMode() ? 'Volume2' : 'VolumeX'" class="w-3.5 h-3.5"></lucide-icon>
              {{ voiceInterviewMode() ? 'VOICE ON' : 'VOICE OFF' }}
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

            <button (click)="toggleSettingsPanel()" class="p-2.5 rounded-xl border border-noir-800 bg-noir-900 text-noir-400 hover:text-white hover:border-crimson-500/40 transition-all">
              <lucide-icon name="Settings" class="w-5 h-5"></lucide-icon>
            </button>
          </div>
        </header>

        <div class="advisor-chat-scroll flex-1 overflow-y-auto px-6 md:px-12 py-12 space-y-12 scroll-smooth">
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

              <div class="group relative space-y-2" [class.text-right]="msg.role === 'user'">
                <div class="inline-block text-left max-w-2xl rounded-3xl p-6 transition-all"
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

        <footer class="p-8 md:p-12 z-20">
          <div class="max-w-4xl mx-auto relative group">
            <div class="absolute -inset-1 bg-gradient-to-r from-crimson-600/20 to-noir-900/20 rounded-3xl blur opacity-0 group-focus-within:opacity-100 transition duration-500"></div>
            
            <div class="relative flex items-end gap-4 bg-noir-900 border border-noir-800 rounded-3xl p-4 focus-within:border-crimson-500/50 transition-all shadow-2xl">
              <button (click)="generateStrategy()" [disabled]="isGenerating()" 
                class="p-3 rounded-2xl text-noir-500 hover:text-crimson-500 hover:bg-crimson-500/5 transition-all">
                <lucide-icon name="Sparkles" class="w-6 h-6"></lucide-icon>
              </button>
              @if (voiceInterviewMode()) {
                <button
                  (click)="toggleListening()"
                  class="p-3 rounded-2xl transition-all border"
                  [class.border-emerald-500/50]="!isListening()"
                  [class.text-emerald-400]="!isListening()"
                  [class.hover:bg-emerald-600/10]="!isListening()"
                  [class.border-red-500/50]="isListening()"
                  [class.text-red-400]="isListening()"
                  [class.bg-red-600/10]="isListening()"
                  [disabled]="!voiceSupported()"
                  title="Toggle microphone"
                >
                  <lucide-icon [name]="isListening() ? 'MicOff' : 'Mic'" class="w-6 h-6"></lucide-icon>
                </button>
              }

              <textarea
                [(ngModel)]="currentInput"
                (keydown.enter)="$event.preventDefault(); sendMessage()"
                rows="1"
                placeholder="Message Rishi..."
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
export class AdvisorComponent implements OnInit, OnDestroy {
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
  voiceInterviewMode = signal(false);
  voiceSupported = signal(false);
  isListening = signal(false);
  isSpeaking = signal(false);

  private recognition: SpeechRecognition | null = null;
  private restartListeningAfterSpeech = false;

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

    this.setupVoiceRecognition();
    this.loadConfig();
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

  ngOnDestroy(): void {
    this.stopListening();
    this.stopSpeaking();
  }

  startNewThread() {
    this.activeThreadId.set(null);
    this.resetMessages();
    this.scrollToBottom();
  }

  startMockInterview() {
    this.startNewThread();
    if (this.voiceSupported()) {
      this.voiceInterviewMode.set(true);
    }
    const prompt = `Let's do a FAANG-style Live Mock Interview. Give me a medium-to-hard algorithmic problem. DO NOT give me any code or hints. I will first explain my approach and data structure choice. You must critique my approach, ask about edge cases, and ask about Big-O complexity BEFORE allowing me to write any code. Act exactly like a strict Senior Engineer interviewer.`;
    const kickoff = this.voiceInterviewMode()
      ? 'Start a sound-based Live Mock Interview. Speak as interviewer and wait for my spoken answer each turn.'
      : 'Start a Live Mock Interview.';
    this.addUserMessage(kickoff, 'text');
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

    this.stopListening();
    this.addUserMessage(text, 'text');
    this.currentInput = '';
    this.scrollToBottom();
    this.askRishi(text, 'text');
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
    this.askRishi(prompt, 'strategy');
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
        if (this.voiceInterviewMode() && responseType === 'text') {
          this.speakAssistantReply(resp.reply || 'Interface timeout.');
        }
        
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
        if (this.voiceInterviewMode()) {
          this.startListening();
        }
      }
    });
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

  toggleVoiceInterview() {
    if (!this.voiceSupported()) {
      this.addAssistantMessage('Voice mode is not supported in this browser. Use Chrome/Edge latest.', 'text');
      return;
    }
    const next = !this.voiceInterviewMode();
    this.voiceInterviewMode.set(next);
    if (!next) {
      this.stopListening();
      this.stopSpeaking();
    }
  }

  toggleListening() {
    if (this.isListening()) {
      this.stopListening();
      return;
    }
    this.startListening();
  }

  private setupVoiceRecognition() {
    const RecognitionCtor = window.SpeechRecognition || window.webkitSpeechRecognition;
    this.voiceSupported.set(!!RecognitionCtor && !!window.speechSynthesis);
    if (!RecognitionCtor) {
      return;
    }
    const rec = new RecognitionCtor();
    rec.continuous = false;
    rec.interimResults = false;
    rec.lang = 'en-US';
    rec.onresult = (event: AppSpeechRecognitionEvent) => {
      const firstResult = event.results?.[0];
      const transcript = firstResult?.[0]?.transcript?.trim() || '';
      if (!transcript) {
        return;
      }
      this.currentInput = transcript;
      this.sendMessage();
    };
    rec.onerror = () => {
      this.isListening.set(false);
    };
    rec.onend = () => {
      this.isListening.set(false);
    };
    this.recognition = rec;
  }

  private startListening() {
    if (!this.voiceInterviewMode() || !this.recognition || this.isGenerating() || this.isSpeaking()) {
      return;
    }
    try {
      this.recognition.start();
      this.isListening.set(true);
    } catch {
      this.isListening.set(false);
    }
  }

  private stopListening() {
    if (!this.recognition) {
      return;
    }
    try {
      this.recognition.stop();
    } catch {
      // no-op
    } finally {
      this.isListening.set(false);
    }
  }

  private stopSpeaking() {
    if (!isPlatformBrowser(this.platformId) || !window.speechSynthesis) {
      return;
    }
    window.speechSynthesis.cancel();
    this.isSpeaking.set(false);
    this.restartListeningAfterSpeech = false;
  }

  private speakAssistantReply(rawText: string) {
    if (!this.voiceInterviewMode() || !isPlatformBrowser(this.platformId) || !window.speechSynthesis) {
      return;
    }
    const stripped = this.stripForSpeech(rawText);
    if (!stripped) {
      this.startListening();
      return;
    }
    this.stopListening();
    this.stopSpeaking();
    this.isSpeaking.set(true);
    this.restartListeningAfterSpeech = true;
    const utterance = new SpeechSynthesisUtterance(stripped);
    utterance.rate = 1;
    utterance.pitch = 1;
    utterance.lang = 'en-US';
    utterance.onend = () => {
      this.isSpeaking.set(false);
      if (this.restartListeningAfterSpeech) {
        this.startListening();
      }
    };
    utterance.onerror = () => {
      this.isSpeaking.set(false);
      if (this.restartListeningAfterSpeech) {
        this.startListening();
      }
    };
    window.speechSynthesis.speak(utterance);
  }

  private stripForSpeech(input: string): string {
    if (!input) {
      return '';
    }
    return input
      .replace(/```[\s\S]*?```/g, ' ')
      .replace(/`([^`]+)`/g, '$1')
      .replace(/\[(.*?)\]\((.*?)\)/g, '$1')
      .replace(/[#>*_\-\|]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
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
}
