import { CommonModule, isPlatformBrowser } from '@angular/common';
import { Component, computed, effect, inject, Injector, OnDestroy, OnInit, PLATFORM_ID, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { LucideAngularModule } from 'lucide-angular';
import { MonacoEditorModule } from 'ngx-monaco-editor-v2';
import { AuthService } from '../core/auth/auth.service';
import { CompilationResult, CompilerService } from '../core/compiler.service';
import { MatchStartPayload, WebSocketService } from '../core/websocket.service';

type ArenaState = 'LOBBY' | 'QUEUED' | 'DUEL' | 'FINISHED';
type DuelLanguage = 'java' | 'python' | 'cpp' | 'javascript';
type StarterCodeMap = Record<DuelLanguage, string>;

@Component({
  selector: 'app-skill-grid',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule, MonacoEditorModule],
  template: `
    <div class="h-full flex flex-col bg-noir-950 text-white animate-fade-in">
      
      <!-- STATE: LOBBY -->
      <div *ngIf="arenaState() === 'LOBBY'" class="flex-1 flex flex-col items-center justify-center text-center p-6 bg-texture">
        <lucide-icon name="swords" class="w-20 h-20 text-crimson-500 mb-6 drop-shadow-[0_0_15px_rgba(220,38,38,0.5)]"></lucide-icon>
        <h1 class="text-5xl font-black tracking-tighter uppercase italic text-white drop-shadow-md mb-4">Will to Power</h1>
        <p class="text-sm font-mono text-noir-300 uppercase tracking-widest leading-relaxed max-w-2xl mb-10">
          Synchronous 1v1 Algorithm Duels. Face an opponent, solve the psychology-infused puzzle faster, and prove your absolute dominance.
        </p>
        
        <button (click)="enterMatchmaking()" class="group relative px-10 py-4 border-2 border-crimson-600 bg-crimson-950/30 overflow-hidden hover:bg-crimson-900/50 transition-all duration-300">
           <div class="absolute inset-0 bg-gradient-to-r from-transparent via-crimson-500/20 to-transparent -translate-x-full group-hover:animate-shimmer"></div>
           <span class="relative text-lg font-black tracking-[0.3em] uppercase text-crimson-400 group-hover:text-white transition-colors">Enter the Crucible</span>
        </button>
      </div>

      <!-- STATE: QUEUED -->
      <div *ngIf="arenaState() === 'QUEUED'" class="flex-1 flex flex-col items-center justify-center p-6 bg-texture relative overflow-hidden">
        <div class="absolute inset-0 bg-crimson-950/10 animate-pulse"></div>
        <lucide-icon name="radar" class="w-16 h-16 text-indigo-500 mb-6 animate-spin-slow"></lucide-icon>
        <h2 class="text-2xl font-black tracking-widest uppercase text-white mb-2 z-10">Searching for Opponent</h2>
        <p class="text-[10px] font-mono text-noir-400 tracking-widest uppercase mb-8 z-10">Connecting to the neural grid...</p>
        
        <button (click)="cancelMatchmaking()" class="px-6 py-2 border border-noir-700 text-[10px] font-black tracking-widest uppercase text-noir-400 hover:text-white hover:border-noir-500 z-10 transition-colors">
          Abort Search
        </button>
      </div>

      <!-- STATE: DUEL/FINISHED -->
      <div *ngIf="arenaState() === 'DUEL' || arenaState() === 'FINISHED'" class="flex-1 flex flex-col overflow-hidden">
        <!-- Top Duel Header -->
        <div class="h-14 border-b border-noir-800 bg-black flex items-center justify-between px-6 shrink-0 shadow-lg z-10">
           <div class="flex items-center gap-4 w-1/3">
              <span class="text-xs font-black uppercase tracking-widest" [class.text-emerald-400]="isMyTurnWinner" [class.text-white]="!isMyTurnWinner">
                 {{ myUsername() }} (YOU)
              </span>
           </div>
           
           <div class="flex items-center justify-center w-1/3 text-center">
              <span class="text-2xl font-black italic tracking-tighter text-crimson-500 mx-4">VS</span>
           </div>

           <div class="flex items-center justify-end gap-4 w-1/3 text-right">
              <span class="text-xs font-black uppercase tracking-widest" [class.text-emerald-400]="isOpponentWinner" [class.text-noir-400]="!isOpponentWinner">
                 {{ opponentUsername() }}
              </span>
           </div>
        </div>

        <!-- 3-Pane Layout -->
        <div class="flex-1 flex flex-row overflow-hidden bg-noir-950">
           
           <!-- LEFT: LOCAL EDITOR -->
           <div class="flex-1 min-w-0 border-r border-noir-800 flex flex-col relative bg-black/20">
              <div class="h-8 border-b border-noir-800 bg-black/40 px-3 flex items-center justify-between">
                 <span class="text-[9px] font-mono text-indigo-400 tracking-widest uppercase flex items-center gap-2">
                    <lucide-icon name="terminal" class="w-3 h-3"></lucide-icon> LOCAL NODE
                 </span>
                 <select [ngModel]="selectedLanguage()" (ngModelChange)="selectLanguage($event)" class="bg-transparent text-[9px] font-mono text-noir-300 uppercase outline-none cursor-pointer">
                    <option value="java">Java</option>
                    <option value="python">Python</option>
                    <option value="cpp">C++</option>
                    <option value="javascript">JavaScript</option>
                 </select>
              </div>

              <!-- Local Editor -->
              <ngx-monaco-editor
                 class="flex-1 w-full min-h-0"
                 [options]="editorOptions"
                 [ngModel]="sourceCode()"
                 (ngModelChange)="onCodeChange($event)"
              ></ngx-monaco-editor>
           </div>

           <!-- CENTER: PUZZLE & CONSOLE -->
           <div class="flex-1 max-w-[40%] flex flex-col min-w-0 border-r border-noir-800 bg-noir-950">
              <div class="p-4 border-b border-noir-800 bg-black/60 shadow-md">
                 <h2 class="text-lg font-black tracking-tighter uppercase mb-1">{{ puzzleTitle() }}</h2>
                 <span class="text-[9px] font-mono text-crimson-400 uppercase tracking-widest border border-crimson-500/30 px-2 py-0.5">{{ puzzleDifficulty() }}</span>
              </div>
              
              <div class="flex-1 overflow-auto p-5 prose prose-invert prose-sm puzzle-content-area text-noir-200" [innerHTML]="puzzleHtml()"></div>

              <!-- Console Bottom Panel -->
              <div class="h-64 border-t border-noir-800 flex flex-col bg-black">
                 <div class="p-2 border-b border-noir-800 flex items-center justify-between gap-2 shrink-0 overflow-x-auto">
                    <button *ngIf="arenaState() !== 'FINISHED'" (click)="executeCode()" [disabled]="isExecuting()" class="flex-1 px-4 py-2 border border-noir-700 text-[9px] font-black uppercase tracking-widest hover:border-white transition-all text-center">
                       {{ isExecuting() ? 'EVALUATING...' : 'TEST RUN' }}
                    </button>
                    <button *ngIf="arenaState() !== 'FINISHED'" (click)="submitCode()" [disabled]="isExecuting()" class="flex-1 px-4 py-2 border border-emerald-600 bg-emerald-900/20 text-emerald-400 hover:bg-emerald-800/40 text-[9px] font-black uppercase tracking-widest transition-all text-center shadow-[0_0_10px_rgba(16,185,129,0.1)]">
                       SUBMIT DUEL
                    </button>
                    <button *ngIf="arenaState() === 'FINISHED'" (click)="leaveDuel()" class="flex-1 px-4 py-2 border border-indigo-600 text-indigo-400 hover:bg-indigo-900/30 text-[9px] font-black uppercase tracking-widest transition-all text-center">
                       RETURN TO LOBBY
                    </button>
                 </div>
                 
                 <div class="flex-1 p-3 overflow-auto font-mono text-[10px] text-noir-400">
                    <div *ngIf="result() as res">
                       <p [class]="res.success ? 'text-emerald-400' : 'text-crimson-400'">[STATUS] {{ res.success ? 'SUCCESS' : 'RUNTIME ERROR' }}</p>
                       <pre class="mt-2 whitespace-pre-wrap">{{ res.error || res.output }}</pre>
                    </div>
                    <div *ngIf="!result() && !isExecuting()">Awaiting instructions...</div>
                    <div *ngIf="isExecuting()" class="flex items-center gap-2 text-indigo-400"><lucide-icon name="loader-2" class="w-3 h-3 animate-spin"></lucide-icon> Executing payload...</div>
                 </div>
              </div>
           </div>

           <!-- RIGHT: OPPONENT EDITOR (READONLY) -->
           <div class="flex-1 min-w-0 flex flex-col relative bg-black/20">
              <div class="h-8 border-b border-noir-800 bg-black/40 px-3 flex items-center justify-between">
                 <span class="text-[9px] font-mono text-crimson-400 tracking-widest uppercase flex items-center gap-2">
                    <lucide-icon name="eye" class="w-3 h-3"></lucide-icon> OPPONENT NODE
                 </span>
                 
                 <!-- Opponent Status Pill -->
                 <span *ngIf="opponentStatusMessage()" class="text-[8px] px-2 py-0.5 border border-noir-700 font-mono tracking-widest uppercase animate-pulse">
                    {{ opponentStatusMessage() }}
                 </span>
              </div>

              <!-- Opponent Editor -->
              <ngx-monaco-editor
                 class="flex-1 w-full min-h-0 opacity-70"
                 [options]="opponentEditorOptions"
                 [ngModel]="opponentCode()"
              ></ngx-monaco-editor>
           </div>

        </div>
      </div>
      
    </div>
  `,
  styles: [`
    :host { display: block; height: 100%; }
    .bg-texture { background-image: radial-gradient(circle at center, rgba(30,30,30,1) 0%, rgba(0,0,0,1) 100%); }
    .puzzle-content-area pre { background: rgba(0,0,0,0.5); border: 1px solid rgba(255,255,255,0.1); padding: 1rem; border-radius: 0.5rem; overflow-x: auto; font-family: monospace; }
    .puzzle-content-area code { background: rgba(255,255,255,0.1); padding: 0.2rem 0.4rem; border-radius: 0.25rem; font-family: monospace; }
  `]
})
export class SkillGridComponent implements OnInit, OnDestroy {
  private authService = inject(AuthService);
  private ws = inject(WebSocketService);
  private compilerService = inject(CompilerService);
  private sanitizer = inject(DomSanitizer);
  private platformId = inject(PLATFORM_ID);
  private injector = inject(Injector);

  arenaState = signal<ArenaState>('LOBBY');
  
  // Local Player Auth
  myUsername = computed(() => this.authService.currentUser()?.name || this.authService.currentUser()?.email || 'UnknownUser');
  myMatchmakingId = computed(() => this.authService.currentUser()?.email || this.authService.currentUser()?.name || 'UnknownUser');
  opponentUsername = signal('Opponent');
  
  // Editor code states
  sourceCode = signal('');
  selectedLanguage = signal<DuelLanguage>('java');
  opponentCode = signal('// Awaiting opponent data...');
  private readonly fallbackStarterCode: StarterCodeMap = {
    java: `class Solution {
    public int solve(int[] nums) {
        int sum = 0;
        for (int n : nums) {
            sum += n;
        }
        return sum;
    }
}`,
    python: `def solve(nums):
    return sum(nums)`,
    cpp: `#include <vector>
using namespace std;

int solve(vector<int> nums) {
    int sum = 0;
    for (int n : nums) {
        sum += n;
    }
    return sum;
}`,
    javascript: `function solve(nums) {
  return nums.reduce((sum, n) => sum + n, 0);
}`,
  };
  private languageDrafts = signal<StarterCodeMap>(this.createDefaultStarters());

  // Match State
  sessionId = signal<string | null>(null);
  puzzleTitle = signal('Loading...');
  puzzleDifficulty = signal('UNK');
  puzzleHtml = signal<SafeHtml>('');
  
  isExecuting = signal(false);
  result = signal<CompilationResult | null>(null);
  
  isMyTurnWinner = false;
  isOpponentWinner = false;
  opponentStatusMessage = signal('');

  editorOptions = {
    theme: 'vs-dark', language: 'java', automaticLayout: true, minimap: { enabled: false },
    fontSize: 14, fontFamily: "'Fira Code', 'Cascadia Code', Consolas, monospace", padding: { top: 16 }
  };

  opponentEditorOptions = {
    theme: 'vs-dark', language: 'java', automaticLayout: true, minimap: { enabled: false }, readOnly: true,
    fontSize: 12, fontFamily: "'Fira Code', 'Cascadia Code', Consolas, monospace", padding: { top: 16 }
  };

  private typingTimeout: any;

  ngOnInit() {
    if (!isPlatformBrowser(this.platformId)) return;
    
    // Listen for match start
    effect(() => {
      const matchStart = this.ws.matchStart();
      if (matchStart && this.arenaState() === 'QUEUED') {
        this.arenaState.set('DUEL');
        this.sessionId.set(matchStart.sessionId);
        
        // Determine opponent
        const me = this.myMatchmakingId();
        const opp = matchStart.player1 === me ? matchStart.player2 : matchStart.player1;
        this.opponentUsername.set(opp);

        // Load puzzle
        const p = matchStart.puzzle;
        this.puzzleTitle.set(p.title);
        this.puzzleDifficulty.set(p.difficulty);
        this.puzzleHtml.set(this.sanitizer.bypassSecurityTrustHtml(p.descriptionHtml));

        const starters = this.resolveStarterCodes(matchStart);
        this.languageDrafts.set(starters);

        const currentLang = this.selectedLanguage();
        this.editorOptions = { ...this.editorOptions, language: currentLang };
        this.sourceCode.set(starters[currentLang] || starters.java);
      }
    }, { allowSignalWrites: true, injector: this.injector });

    // Listen for opponent typing
    effect(() => {
      const typing = this.ws.opponentTyping();
      if (typing && typing.username !== this.myMatchmakingId() && typing.codeDelta) {
        this.opponentCode.set(typing.codeDelta);
      }
    }, { allowSignalWrites: true, injector: this.injector });

    // Listen for opponent execution status
    effect(() => {
      const oppStatus = this.ws.opponentStatus();
      if (oppStatus && oppStatus.username !== this.myMatchmakingId()) {
        if (oppStatus.status === 'SUBMITTED_SUCCESS') {
           this.isOpponentWinner = true;
           this.opponentStatusMessage.set('VICTORY ACHIEVED');
           this.arenaState.set('FINISHED');
        } else {
           this.opponentStatusMessage.set(oppStatus.status || 'EXECUTING...');
           setTimeout(() => this.opponentStatusMessage.set(''), 3000);
        }
      }
    }, { allowSignalWrites: true, injector: this.injector });
  }

  ngOnDestroy() {
    this.cancelMatchmaking();
  }

  enterMatchmaking() {
    this.arenaState.set('QUEUED');
    this.ws.subscribeToMatchmaking(this.myMatchmakingId())
      .then(() => this.ws.joinLobby().subscribe());
  }

  cancelMatchmaking() {
    if (this.arenaState() === 'QUEUED') {
       this.ws.leaveLobby().subscribe();
       this.arenaState.set('LOBBY');
    }
  }

  leaveDuel() {
     this.arenaState.set('LOBBY');
     this.sessionId.set(null);
     this.ws.matchStart.set(null);
     this.isMyTurnWinner = false;
     this.isOpponentWinner = false;
     this.selectedLanguage.set('java');
     this.editorOptions = { ...this.editorOptions, language: 'java' };
     this.languageDrafts.set(this.createDefaultStarters());
     this.sourceCode.set('');
     this.opponentCode.set('');
  }

  selectLanguage(lang: string) {
     const nextLanguage = this.normalizeLanguage(lang);
     const previousLanguage = this.selectedLanguage();
     const updatedDrafts: StarterCodeMap = {
        ...this.languageDrafts(),
        [previousLanguage]: this.sourceCode()
     };

     this.languageDrafts.set(updatedDrafts);
     this.selectedLanguage.set(nextLanguage);
     this.editorOptions = { ...this.editorOptions, language: nextLanguage };
     this.sourceCode.set(updatedDrafts[nextLanguage] || this.fallbackStarterCode[nextLanguage]);
     this.result.set(null);
  }

  onCodeChange(newCode: string) {
     this.sourceCode.set(newCode);
     const currentLanguage = this.selectedLanguage();
     this.languageDrafts.update((drafts) => ({
        ...drafts,
        [currentLanguage]: newCode || ''
     }));
     
     // Throttle sending WS update
     if (this.typingTimeout) clearTimeout(this.typingTimeout);
     this.typingTimeout = setTimeout(() => {
        if (this.sessionId()) {
           this.ws.sendTypingDelta(this.sessionId()!, this.myMatchmakingId(), newCode);
        }
     }, 500);
  }

  executeCode() {
     if (this.isExecuting() || !this.sourceCode().trim()) return;
     this.isExecuting.set(true);
     
     if (this.sessionId()) this.ws.sendExecuteStatus(this.sessionId()!, this.myMatchmakingId(), 'TESTING');

     // Using the existing local dry-run compile endpoint (not LeetCode)
     this.compilerService.executeCode({
         sourceCode: this.sourceCode(),
         language: this.selectedLanguage(),
         input: '',
         timeoutSeconds: 10
     }).subscribe({
         next: (res) => { 
            this.result.set(res); 
            this.isExecuting.set(false); 
            if (this.sessionId()) this.ws.sendExecuteStatus(this.sessionId()!, this.myMatchmakingId(), res.success ? 'TEST PASSED' : 'TEST FAILED');
         },
         error: (err) => {
            this.result.set({ success: false, output: '', error: err?.error?.error || 'Failed', executionTime: '0ms', language: '', timestamp: '' });
            this.isExecuting.set(false);
            if (this.sessionId()) this.ws.sendExecuteStatus(this.sessionId()!, this.myMatchmakingId(), 'TEST FAILED');
         }
     });
  }

  submitCode() {
     // A dummy submission logic for the MVP since evaluating hidden test cases locally needs a proper sandbox.
     // For now, if the basic code compiles, we declare victory!
     if (this.isExecuting() || !this.sourceCode().trim()) return;
     this.isExecuting.set(true);

     this.compilerService.executeCode({
         sourceCode: this.sourceCode(),
         language: this.selectedLanguage(),
         input: '',
         timeoutSeconds: 10
     }).subscribe({
         next: (res) => { 
            this.result.set(res); 
            this.isExecuting.set(false); 
            if (res.success) {
               this.isMyTurnWinner = true;
               this.arenaState.set('FINISHED');
               if (this.sessionId()) {
                  this.ws.sendExecuteStatus(this.sessionId()!, this.myMatchmakingId(), 'SUBMITTED_SUCCESS');
               }
            } else {
               if (this.sessionId()) this.ws.sendExecuteStatus(this.sessionId()!, this.myMatchmakingId(), 'SUBMITTED_FAILED');
            }
         },
         error: (err) => {
            this.result.set({ success: false, output: '', error: err?.error?.error || 'Failed', executionTime: '0ms', language: '', timestamp: '' });
            this.isExecuting.set(false);
            if (this.sessionId()) this.ws.sendExecuteStatus(this.sessionId()!, this.myMatchmakingId(), 'SUBMITTED_FAILED');
         }
     });
  }

  private resolveStarterCodes(matchStart: MatchStartPayload): StarterCodeMap {
     const roundStarterCodes = this.extractRoundStarterCodes(matchStart.puzzle.roundsJson);
     return {
        java: this.normalizeStarterCode(roundStarterCodes.java || matchStart.puzzle.starterCodeJava, 'java'),
        python: this.normalizeStarterCode(roundStarterCodes.python || matchStart.puzzle.starterCodePython, 'python'),
        cpp: this.normalizeStarterCode(roundStarterCodes.cpp || matchStart.puzzle.starterCodeCpp, 'cpp'),
        javascript: this.normalizeStarterCode(roundStarterCodes.javascript || matchStart.puzzle.starterCodeJavascript, 'javascript'),
     };
  }

  private extractRoundStarterCodes(roundsJson: string): Partial<StarterCodeMap> {
     if (!roundsJson) return {};

     try {
        const parsed = JSON.parse(roundsJson);
        if (!Array.isArray(parsed)) return {};

        const codingRound = parsed.find((round: any) => String(round?.type || '').toUpperCase() === 'CODING');
        const starterCode = codingRound?.starterCode;
        if (!starterCode || typeof starterCode !== 'object') return {};

        return {
          java: typeof starterCode.java === 'string' ? starterCode.java : undefined,
          python: typeof starterCode.python === 'string' ? starterCode.python : undefined,
          cpp: typeof starterCode.cpp === 'string' ? starterCode.cpp : undefined,
          javascript: typeof starterCode.javascript === 'string' ? starterCode.javascript : undefined,
        };
     } catch {
        return {};
     }
  }

  private normalizeStarterCode(code: string | undefined, language: DuelLanguage): string {
     const candidate = (code || '').trim();
     if (!candidate || this.isPlaceholderStarterCode(candidate)) {
        return this.fallbackStarterCode[language];
     }
     return candidate;
  }

  private isPlaceholderStarterCode(code: string): boolean {
     const normalized = code.toLowerCase().replace(/\s+/g, ' ').trim();
     const containsPlaceholderEllipsis = /\.\.\.(?!\w)/.test(code);
     return normalized === '...'
        || containsPlaceholderEllipsis
        || normalized.includes('class solution { ... }')
        || normalized.includes('def solve(...): ...')
        || normalized.includes('function solve(...) { ... }')
        || normalized.includes('#include <vector>\\nusing namespace std;\\n...');
  }

  private createDefaultStarters(): StarterCodeMap {
     return { ...this.fallbackStarterCode };
  }

  private normalizeLanguage(lang: string): DuelLanguage {
     switch (lang) {
        case 'python':
        case 'cpp':
        case 'javascript':
        case 'java':
          return lang;
        default:
          return 'java';
     }
  }
}
