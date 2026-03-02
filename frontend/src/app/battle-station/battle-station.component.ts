import { CommonModule } from '@angular/common';
import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { MonacoEditorModule } from 'ngx-monaco-editor-v2';
import { Subscription } from 'rxjs';
import {
    CompilationResult,
    CompilerInfo,
    CompilerService,
    LeetCodeSubmissionResponse,
    RishiCodeChangeEvent,
    RishiCompileAttemptAnalysisResponse,
    RishiCompileAttemptRequest
} from '../core/compiler.service';
import { LeetCodeQuestion, QuestionBankService } from '../core/question-bank.service';

export type StressMode = 'NORMAL' | 'ONE_SHOT' | 'BLITZ' | 'BLINDFOLD' | 'IRON_MAN';
export type CognitiveTrack = 'HEURISTIC_FLOW' | 'SYSTEM2_OVERRIDE' | 'FAULT_TOLERANCE' | 'AFFECTIVE_REGULATION';
type RishiSessionState = 'WAITING' | 'TYPING' | 'CURSOR_IDLE' | 'EDITOR_UNFOCUSED' | 'TAB_HIDDEN';

@Component({
  selector: 'app-battle-station',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule, MonacoEditorModule],
  templateUrl: './battle-station.component.html',
  styleUrl: './battle-station.component.css'
})
export class BattleStationComponent implements OnInit, OnDestroy {
  private compilerService = inject(CompilerService);
  private questionBankService = inject(QuestionBankService);
  private sanitizer = inject(DomSanitizer);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  private routeSubscription: Subscription | null = null;
  private questionDetailRequestToken = 0;
  private editorChangeDisposable: { dispose: () => void } | null = null;
  private editorFocusDisposable: { dispose: () => void } | null = null;
  private editorBlurDisposable: { dispose: () => void } | null = null;
  private editorCursorDisposable: { dispose: () => void } | null = null;
  private telemetryFlushHandle: ReturnType<typeof setInterval> | null = null;
  private activityTimerHandle: ReturnType<typeof setInterval> | null = null;
  private compilerLockTimerHandle: ReturnType<typeof setInterval> | null = null;
  private telemetryBuffer: RishiCodeChangeEvent[] = [];
  private telemetrySessionStartMs = 0;
  private sessionActiveDurationMs = 0;
  private sessionTypingDurationMs = 0;
  private sessionCursorIdleDurationMs = 0;
  private sessionEditorUnfocusedDurationMs = 0;
  private sessionTabHiddenDurationMs = 0;
  private lastEditorInteractionMs = 0;
  private lastTypingMs = 0;
  private hasUserStartedCoding = false;
  private isEditorTextFocused = false;
  private isWindowFocused = true;
  private isDocumentVisible = true;
  private readonly typingRecencyWindowMs = 4000;
  private readonly handleVisibilityChange = () => {
    this.isDocumentVisible = typeof document === 'undefined' ? true : !document.hidden;
    if (!this.isDocumentVisible) {
      this.rishiSessionState.set('TAB_HIDDEN');
      this.rishiSessionIdle.set(true);
      return;
    }
    this.updateRishiSessionState(Date.now());
  };
  private readonly handleWindowBlur = () => {
    this.isWindowFocused = false;
    this.rishiSessionState.set('TAB_HIDDEN');
    this.rishiSessionIdle.set(true);
  };
  private readonly handleWindowFocus = () => {
    this.isWindowFocused = true;
    this.updateRishiSessionState(Date.now());
  };

  // Compiler state
  availableLanguages = signal<CompilerInfo[]>([]);
  selectedLanguage = signal('java');
  sourceCode = signal('');
  editorOptions = {
    theme: 'vs-dark',
    language: 'java',
    automaticLayout: true,
    minimap: { enabled: false },
    fontSize: 14
  };
  stdinInput = signal('');
  isExecuting = signal(false);
  result = signal<CompilationResult | null>(null);
  timeoutSeconds = signal(10);
  isSetupPanelCollapsed = signal(true);
  rishiCodingSessionId = signal<number | null>(null);
  rishiSessionElapsedMs = signal(0);
  rishiSessionActiveMs = signal(0);
  rishiSessionState = signal<RishiSessionState>('WAITING');
  rishiSessionIdle = signal(true);

  // Compiler lock state (set by Rishi agent)
  isCompilerLocked = signal(false);
  compilerLockReason = signal<string | null>(null);
  compilerLockRemainingSeconds = signal(0);


  // Question state
  isQuestionBankLoading = signal(false);
  isQuestionLoading = signal(false);
  questionLoadError = signal<string | null>(null);
  questions = signal<LeetCodeQuestion[]>([]);
  questionCategory = signal<'all' | 'common' | 'top-tier' | 'trending'>('all');
  questionDifficulty = signal<'all' | 'easy' | 'medium' | 'hard'>('all');
  questionSearch = signal('');
  questionPage = signal(1);
  readonly questionPageSize = 8;
  selectedQuestion = signal<LeetCodeQuestion | null>(null);
  questionHtml = signal<SafeHtml | null>(null);

  filteredQuestions = computed(() => {
    const difficulty = this.questionDifficulty();
    const search = this.questionSearch().trim().toLowerCase();
    let allQuestions = this.questions();

    if (difficulty !== 'all') {
      allQuestions = allQuestions.filter((question) =>
        (question.difficulty || '').toLowerCase() === difficulty
      );
    }

    if (!search) {
      return allQuestions;
    }

    return allQuestions.filter((question) =>
      question.title.toLowerCase().includes(search) ||
      question.slug?.toLowerCase().includes(search) ||
      question.tags.some((tag) => tag.toLowerCase().includes(search))
    );
  });
  paginatedQuestions = computed(() => {
    const start = (this.questionPage() - 1) * this.questionPageSize;
    return this.filteredQuestions().slice(start, start + this.questionPageSize);
  });
  questionTotalPages = computed(() => {
    const pages = Math.ceil(this.filteredQuestions().length / this.questionPageSize);
    return Math.max(1, pages);
  });
  questionEasyCount = computed(() => this.questions().filter((question) => question.difficulty === 'Easy').length);
  questionMediumCount = computed(() => this.questions().filter((question) => question.difficulty === 'Medium').length);
  questionHardCount = computed(() => this.questions().filter((question) => question.difficulty === 'Hard').length);

  // LeetCode auth state (stored securely on backend)
  isLeetCodeConnected = signal(false);
  isLeetCodeAuthLoading = signal(false);

  // LeetCode submission state
  isSubmittingLeetCode = signal(false);
  submissionResponse = signal<LeetCodeSubmissionResponse | null>(null);
  submissionError = signal<string | null>(null);
  rishiRunInsight = signal<RishiCompileAttemptAnalysisResponse | null>(null);
  rishiSubmissionInsight = signal<RishiCompileAttemptAnalysisResponse | null>(null);
  showEmotionModal = signal(false);
  isSavingEmotion = signal(false);
  canSubmitToLeetCode = computed(() => {
    return Boolean(
      this.selectedQuestion()?.slug &&
        this.sourceCode().trim() &&
        this.isLeetCodeConnected() &&
        !this.isSubmittingLeetCode()
    );
  });
  submitGateReason = computed(() => {
    if (this.isCompilerLocked()) {
      return `🔒 Compiler locked: ${this.compilerLockReason() || 'Agent intervention'} (${this.compilerLockRemainingSeconds()}s)`;
    }
    if (this.isSubmittingLeetCode()) {
      return 'Submitting to LeetCode...';
    }
    if (!this.selectedQuestion()?.slug) {
      return 'Select a question to enable submit.';
    }
    if (!this.sourceCode().trim()) {
      return 'Write code to enable submit.';
    }
    if (this.isLeetCodeAuthLoading()) {
      return 'Checking LeetCode connection...';
    }
    if (!this.isLeetCodeConnected()) {
      return 'Connect LeetCode in Settings to enable submit.';
    }
    return '';
  });

  askRishiForReview() {
    const code = this.sourceCode().trim();
    if (!code) return;

    const question = this.selectedQuestion()?.title || 'Unknown Problem';
    const lang = this.selectedLanguage();
    
    let contextStr = `I am working on the problem: "${question}" in ${lang}.\n\nHere is my current code:\n\`\`\`${lang}\n${code}\n\`\`\``;

    const currResult = this.result();
    if (currResult && currResult.output) {
      contextStr += `\n\nWhen I ran it locally, the output was:\n\`\`\`\n${currResult.output}\n\`\`\``;
    }
    if (currResult && currResult.error) {
      contextStr += `\n\nI got this error:\n\`\`\`\n${currResult.error}\n\`\`\``;
    }

    const subResponse = this.submissionResponse();
    if (subResponse) {
      contextStr += `\n\nWhen I submitted it to LeetCode, the result was: ${subResponse.status || 'Unknown'}`;
      
      const judge = subResponse.judgeResult as any;
      if (judge) {
        if (judge.status_msg) {
          contextStr += ` (${judge.status_msg})`;
        }
        if (judge.compile_error) {
          contextStr += `\n\nCompile Error:\n\`\`\`\n${judge.compile_error}\n\`\`\``;
        }
        if (judge.runtime_error) {
          contextStr += `\n\nRuntime Error:\n\`\`\`\n${judge.runtime_error}\n\`\`\``;
        }
      }
    }

    contextStr += '\n\nPlease act as a Senior Developer. Review my code. Point out any algorithmic inefficiencies, edge cases I missed, or how I can make it more idiomatic. Do not just write the solution for me; guide me.';

    sessionStorage.setItem('rishi_pending_context', contextStr);
    this.router.navigate(['/advisor']);
  }

  // Boilerplate templates
  private boilerplates: Record<string, string> = {
    java: `class Solution {
    public int[] twoSum(int[] nums, int target) {
        return new int[] {0, 1};
    }
}`,
    python: `class Solution:
    def twoSum(self, nums, target):
        return [0, 1]`,
    cpp: `#include <vector>
using namespace std;

class Solution {
public:
    vector<int> twoSum(vector<int>& nums, int target) {
        return {0, 1};
    }
};`,
    javascript: `var twoSum = function(nums, target) {
    return [0, 1];
};`
  };

  ngOnInit() {
    this.registerRishiPresenceTracking();
    this.registerRouteQuestionSync();
    this.loadAvailableLanguages();
    this.loadQuestions('all');
    this.loadLeetCodeAuthStatus();
    this.sourceCode.set(this.boilerplates['java']);
    this.startRishiCodingSession();
    this.startRishiActivityTimer();
  }

  toggleSetupPanel() {
    this.isSetupPanelCollapsed.update((current) => !current);
  }

  ngOnDestroy() {
    this.routeSubscription?.unsubscribe();
    this.editorChangeDisposable?.dispose();
    this.editorFocusDisposable?.dispose();
    this.editorBlurDisposable?.dispose();
    this.editorCursorDisposable?.dispose();
    if (this.telemetryFlushHandle) {
      clearInterval(this.telemetryFlushHandle);
      this.telemetryFlushHandle = null;
    }
    if (this.activityTimerHandle) {
      clearInterval(this.activityTimerHandle);
      this.activityTimerHandle = null;
    }
    if (this.compilerLockTimerHandle) {
      clearInterval(this.compilerLockTimerHandle);
      this.compilerLockTimerHandle = null;
    }
    this.unregisterRishiPresenceTracking();
    this.endRishiCodingSession('battle_station_closed');
  }

  private activateCompilerLock(reason: string, remainingSeconds: number) {
    this.isCompilerLocked.set(true);
    this.compilerLockReason.set(reason);
    this.compilerLockRemainingSeconds.set(Math.max(0, Math.floor(remainingSeconds)));

    if (this.compilerLockTimerHandle) {
      clearInterval(this.compilerLockTimerHandle);
    }
    this.compilerLockTimerHandle = setInterval(() => {
      const remaining = this.compilerLockRemainingSeconds() - 1;
      if (remaining <= 0) {
        this.isCompilerLocked.set(false);
        this.compilerLockReason.set(null);
        this.compilerLockRemainingSeconds.set(0);
        if (this.compilerLockTimerHandle) {
          clearInterval(this.compilerLockTimerHandle);
          this.compilerLockTimerHandle = null;
        }
      } else {
        this.compilerLockRemainingSeconds.set(remaining);
      }
    }, 1000);
  }

  private loadAvailableLanguages() {
    this.compilerService.getAvailableLanguages().subscribe({
      next: (languages) => {
        const normalizedLanguages = this.deduplicateLanguages(languages);
        this.availableLanguages.set(normalizedLanguages);
        if (!normalizedLanguages.length) {
          this.selectLanguage('java');
          return;
        }

        const current = this.normalizeLanguageCommand(this.selectedLanguage());
        const active = normalizedLanguages.find((lang) => lang.command === current);
        this.selectLanguage(active?.command || normalizedLanguages[0].command);
      },
      error: () => {
        this.selectLanguage('java');
      }
    });
  }

  private loadQuestions(category: 'all' | 'common' | 'top-tier' | 'trending') {
    this.questionCategory.set(category);
    this.questionPage.set(1);
    this.isQuestionBankLoading.set(true);
    this.questionLoadError.set(null);

    const request$ = category === 'all'
      ? this.questionBankService.getAllQuestions()
      : category === 'common'
        ? this.questionBankService.getCommonQuestions()
        : category === 'top-tier'
          ? this.questionBankService.getTopTierQuestions()
          : this.questionBankService.getTrendingQuestions();

    request$.subscribe({
      next: (questions) => {
        const normalized = questions.map((question) => this.normalizeQuestion(question));
        this.questions.set(normalized);
        this.isQuestionBankLoading.set(false);

        this.syncSelectedQuestionWithLoadedData();

        if (!this.selectedQuestion() && normalized.length > 0) {
          this.selectQuestion(normalized[0], false);
        }
      },
      error: () => {
        this.isQuestionBankLoading.set(false);
        this.questionLoadError.set('Question bank unavailable. Reload and try again.');
      }
    });
  }

  private registerRouteQuestionSync() {
    this.routeSubscription = this.route.queryParamMap.subscribe((params) => {
      const slug = (params.get('slug') || '').trim();
      const title = (params.get('title') || '').trim();
      const difficulty = (params.get('difficulty') || '').trim();
      const url = (params.get('url') || '').trim();
      const tagsParam = (params.get('tags') || '').trim();

      if (!slug && !title && !url) {
        return;
      }

      const question = this.normalizeQuestion({
        slug,
        title: title || this.humanizeSlug(slug) || 'Untitled Problem',
        difficulty: difficulty || 'Unknown',
        url: url || this.toLeetCodeUrl(slug),
        tags: this.parseTags(tagsParam)
      });

      if (this.isSameQuestion(question, this.selectedQuestion())) {
        return;
      }

      this.selectQuestion(question, false);
    });
  }

  private loadLeetCodeAuthStatus() {
    this.isLeetCodeAuthLoading.set(true);
    this.compilerService.getLeetCodeAuthStatus().subscribe({
      next: (status) => {
        this.isLeetCodeConnected.set(status.connected === true);
        this.isLeetCodeAuthLoading.set(false);
      },
      error: () => {
        this.isLeetCodeConnected.set(false);
        this.isLeetCodeAuthLoading.set(false);
      }
    });
  }

  goToLeetCodeSettings() {
    this.router.navigate(['/settings']);
  }

  private normalizeQuestion(question: Partial<LeetCodeQuestion>): LeetCodeQuestion {
    const url = typeof question.url === 'string' ? question.url : '';
    const derivedSlug = this.extractSlug(url);
    const slug =
      (typeof question.slug === 'string' && question.slug.trim()) ||
      derivedSlug ||
      '';

    return {
      title: typeof question.title === 'string' && question.title.trim()
        ? question.title.trim()
        : this.humanizeSlug(slug) || 'Untitled Problem',
      difficulty: typeof question.difficulty === 'string' && question.difficulty.trim()
        ? question.difficulty.trim()
        : 'Unknown',
      url: url || this.toLeetCodeUrl(slug),
      tags: Array.isArray(question.tags)
        ? question.tags.filter((tag): tag is string => typeof tag === 'string')
        : [],
      slug
    };
  }

  private syncSelectedQuestionWithLoadedData() {
    const selected = this.selectedQuestion();
    const loaded = this.questions();

    if (!selected || !loaded.length) {
      return;
    }

    const selectedSlug = (selected.slug || '').toLowerCase();
    const selectedUrl = (selected.url || '').toLowerCase();
    const match = loaded.find((question) => {
      const questionSlug = (question.slug || '').toLowerCase();
      const questionUrl = (question.url || '').toLowerCase();
      return (selectedSlug && questionSlug === selectedSlug) || (selectedUrl && questionUrl === selectedUrl);
    });

    if (match) {
      this.selectedQuestion.set(match);
    }
  }

  selectLanguage(lang: string) {
    const normalizedLang = this.normalizeLanguageCommand(lang);
    this.selectedLanguage.set(normalizedLang);
    this.editorOptions = { ...this.editorOptions, language: normalizedLang };

    const currentCode = this.sourceCode();
    const isBoilerplate = Object.values(this.boilerplates).some((snippet) => snippet === currentCode);
    if (!currentCode.trim() || isBoilerplate) {
      this.sourceCode.set(this.boilerplates[normalizedLang] || this.boilerplates['java']);
    }
  }

  onCodeChange(nextCode: string) {
    this.sourceCode.set(nextCode || '');
  }

  onEditorInit(editor: any) {
    this.editorChangeDisposable?.dispose();
    this.editorFocusDisposable?.dispose();
    this.editorBlurDisposable?.dispose();
    this.editorCursorDisposable?.dispose();

    this.editorFocusDisposable = editor?.onDidFocusEditorText?.(() => {
      this.isEditorTextFocused = true;
      this.markEditorInteraction(false);
    });

    this.editorBlurDisposable = editor?.onDidBlurEditorText?.(() => {
      this.isEditorTextFocused = false;
      this.updateRishiSessionState(Date.now());
    });

    this.editorCursorDisposable = editor?.onDidChangeCursorPosition?.((event: any) => {
      if (!this.isEditorTextFocused) {
        return;
      }
      const source = typeof event?.source === 'string' ? event.source.toLowerCase() : '';
      if (source === 'api') {
        return;
      }
      this.markEditorInteraction(false);
    });

    this.editorChangeDisposable = editor?.onDidChangeModelContent?.((event: any) => {
      if (!this.isUserEditorChangeEvent(event)) {
        return;
      }

      this.markEditorInteraction(true);
      const model = editor.getModel?.();
      const resultingCodeLength = model?.getValueLength?.() ?? this.sourceCode().length;
      const editorVersion = model?.getVersionId?.() ?? 0;
      const nowIso = new Date().toISOString();

      for (const change of event?.changes || []) {
        this.telemetryBuffer.push({
          timestamp: nowIso,
          editorVersion,
          rangeOffset: change?.rangeOffset ?? 0,
          rangeLength: change?.rangeLength ?? 0,
          insertedChars: (change?.text || '').length,
          deletedChars: change?.rangeLength ?? 0,
          resultingCodeLength,
          activityState: this.rishiSessionState(),
          editorFocused: this.isEditorTextFocused,
          windowFocused: this.isWindowFocused,
          documentVisible: this.isDocumentVisible
        });
      }

      if (this.telemetryBuffer.length >= 80) {
        this.flushRishiCodeChanges();
      }
    });
  }

  onQuestionSearchChange(event: Event) {
    const target = event.target as HTMLInputElement;
    this.questionSearch.set(target.value || '');
    this.questionPage.set(1);
  }

  setQuestionCategory(category: 'all' | 'common' | 'top-tier' | 'trending') {
    if (this.questionCategory() === category) {
      return;
    }
    this.loadQuestions(category);
  }

  setQuestionDifficulty(difficulty: 'all' | 'easy' | 'medium' | 'hard') {
    this.questionDifficulty.set(difficulty);
    this.questionPage.set(1);
  }

  nextQuestionPage() {
    if (this.questionPage() < this.questionTotalPages()) {
      this.questionPage.update((current) => current + 1);
    }
  }

  prevQuestionPage() {
    if (this.questionPage() > 1) {
      this.questionPage.update((current) => current - 1);
    }
  }

  selectQuestion(question: LeetCodeQuestion, updateRoute = true) {
    const normalized = this.normalizeQuestion(question);

    if (this.isSameQuestion(normalized, this.selectedQuestion()) && this.questionHtml()) {
      if (updateRoute) {
        this.router.navigate([], {
          relativeTo: this.route,
          queryParams: {
            slug: normalized.slug || null,
            title: normalized.title || null,
            difficulty: normalized.difficulty || null,
            url: normalized.url || null,
            tags: normalized.tags.length ? normalized.tags.join(',') : null
          },
          queryParamsHandling: 'merge',
          replaceUrl: true
        });
      }
      return;
    }

    this.selectedQuestion.set(normalized);
    this.submissionResponse.set(null);
    this.submissionError.set(null);
    this.questionHtml.set(null);

    if (normalized.slug || normalized.title) {
      const requestToken = ++this.questionDetailRequestToken;
      this.isQuestionLoading.set(true);
      this.compilerService.getLeetCodeQuestionDetails(normalized.slug || '', normalized.title, normalized.url).subscribe({
        next: (res) => {
          if (requestToken !== this.questionDetailRequestToken) {
            return;
          }
          const content = this.extractQuestionContent(res);
          if (content) {
             this.questionHtml.set(this.sanitizer.bypassSecurityTrustHtml(content));
          } else {
             this.questionHtml.set(this.sanitizer.bypassSecurityTrustHtml(`<p>Description unavailable via API. <a href="${normalized.url}" target="_blank" class="text-indigo-400">View on LeetCode</a></p>`));
          }
          this.isQuestionLoading.set(false);
        },
        error: () => {
          if (requestToken !== this.questionDetailRequestToken) {
            return;
          }
          this.questionHtml.set(this.sanitizer.bypassSecurityTrustHtml(`<p class="text-crimson-400">Failed to load problem description. <a href="${normalized.url}" target="_blank" class="text-indigo-400 underline">View on LeetCode</a></p>`));
          this.isQuestionLoading.set(false);
        }
      });
    } else {
      this.isQuestionLoading.set(false);
    }

    if (!updateRoute) {
      return;
    }

    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        slug: normalized.slug || null,
        title: normalized.title || null,
        difficulty: normalized.difficulty || null,
        url: normalized.url || null,
        tags: normalized.tags.length ? normalized.tags.join(',') : null
      },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }



  selectQuestionBySlugOrUrl(slugOrUrl: string) {
    const normalizedKey = (slugOrUrl || '').toLowerCase();
    if (!normalizedKey) {
      return;
    }

    const match = this.questions().find((question) => {
      const slug = (question.slug || '').toLowerCase();
      const url = (question.url || '').toLowerCase();
      return slug === normalizedKey || url === normalizedKey;
    });

    if (match) {
      this.selectQuestion(match);
    }
  }

  openSelectedQuestion() {
    const url = this.selectedQuestion()?.url;
    if (!url) {
      return;
    }
    window.open(url, '_blank', 'noopener');
  }

  executeCode() {
    if (this.isCompilerLocked()) {
      this.result.set({
        success: false, output: '',
        error: `🔒 Compiler locked by Rishi: ${this.compilerLockReason() || 'Agent intervention'}. Unlocks in ${this.compilerLockRemainingSeconds()}s.`,
        executionTime: '0ms', language: this.selectedLanguage(), timestamp: new Date().toISOString()
      });
      return;
    }
    if (this.isExecuting() || !this.sourceCode().trim()) {
      return;
    }

    this.markRishiActivity();
    this.isExecuting.set(true);
    this.result.set(null);
    this.rishiRunInsight.set(null);

    this.compilerService.executeCode({
      sourceCode: this.sourceCode(),
      language: this.selectedLanguage(),
      input: this.stdinInput(),
      timeoutSeconds: this.timeoutSeconds(),
      problemSlug: this.selectedQuestion()?.slug
    }).subscribe({
      next: (executionResult) => {
        this.result.set(executionResult);
        this.recordRishiCompileAttempt({
          source: 'battle_run_local',
          success: executionResult.success,
          executionTimeMs: this.parseExecutionTimeMs(executionResult.executionTime),
          errorMessage: executionResult.error || '',
          outputPreview: executionResult.output || ''
        }, (insight) => this.rishiRunInsight.set(insight));
        this.isExecuting.set(false);
      },
      error: (err) => {
        // Handle 423 Locked from Rishi agent
        if (err?.status === 423) {
          this.activateCompilerLock(
            err?.error?.reason || 'Agent intervention',
            err?.error?.remainingSeconds ?? 300
          );
        }
        const failedResult: CompilationResult = {
          success: false,
          output: '',
          error: err?.status === 423
            ? `🔒 Compiler locked by Rishi: ${err?.error?.reason || 'Agent intervention'}. Unlocks in ${err?.error?.remainingSeconds ?? '?'}s.`
            : (err?.error?.error || 'Execution failed. Compiler service unavailable.'),
          executionTime: '0ms',
          language: this.selectedLanguage(),
          timestamp: new Date().toISOString()
        };
        this.result.set(failedResult);
        this.recordRishiCompileAttempt({
          source: 'battle_run_local',
          success: false,
          executionTimeMs: 0,
          errorMessage: failedResult.error,
          outputPreview: failedResult.output
        }, (insight) => this.rishiRunInsight.set(insight));
        this.isExecuting.set(false);
      }
    });
  }

  submitToLeetCode() {
    if (this.isCompilerLocked()) {
      this.submissionError.set(`🔒 Compiler locked by Rishi: ${this.compilerLockReason() || 'Agent intervention'}. Unlocks in ${this.compilerLockRemainingSeconds()}s.`);
      return;
    }
    if (this.isSubmittingLeetCode()) {
      return;
    }
    this.markRishiActivity();

    const question = this.selectedQuestion();
    if (!question?.slug) {
      this.submissionError.set('Select a question first.');
      return;
    }

    if (!this.sourceCode().trim()) {
      this.submissionError.set('Code cannot be empty.');
      return;
    }

    if (!this.isLeetCodeConnected()) {
      this.submissionError.set('Connect LeetCode once before submitting.');
      return;
    }

    this.isSubmittingLeetCode.set(true);
    this.submissionError.set(null);
    this.submissionResponse.set(null);
    this.rishiSubmissionInsight.set(null);

    this.compilerService.submitToLeetCode({
      sourceCode: this.sourceCode(),
      language: this.selectedLanguage(),
      problemSlug: question.slug,
      waitForResult: true
    }).subscribe({
      next: (response) => {
        this.submissionResponse.set(response);
        const accepted = this.isAcceptedStatus(response?.status);
        const judge = response?.judgeResult as Record<string, unknown> | undefined;
        const statusMsg = typeof judge?.['status_msg'] === 'string' ? judge['status_msg'] : '';
        const compileError = typeof judge?.['compile_error'] === 'string' ? judge['compile_error'] : '';
        const runtimeError = typeof judge?.['runtime_error'] === 'string' ? judge['runtime_error'] : '';
        const testsTotal = this.extractJudgeCount(judge, ['total_testcases', 'totalTestcases', 'total_tests']);
        const testsPassed = this.extractJudgeCount(judge, ['total_correct', 'passed_testcases', 'tests_passed']);
        const failedTestInput = this.extractJudgeText(judge, ['last_testcase', 'failed_test_input', 'input']);
        const expectedOutput = this.extractJudgeText(judge, ['expected_output', 'expectedOutput']);
        const actualOutput = this.extractJudgeText(judge, ['code_output', 'actual_output', 'actualOutput', 'stdout']);
        this.recordRishiCompileAttempt({
          source: 'leetcode_submit',
          success: accepted,
          executionTimeMs: 0,
          submissionStatus: response?.status || '',
          judgeMessage: statusMsg,
          errorMessage: compileError || runtimeError || (!accepted ? statusMsg : ''),
          outputPreview: '',
          testsTotal: testsTotal > 0 ? testsTotal : undefined,
          testsPassed: testsPassed >= 0 ? testsPassed : undefined,
          failedTestInput: failedTestInput || undefined,
          expectedOutput: expectedOutput || undefined,
          actualOutput: actualOutput || undefined,
          stackTraceSnippet: runtimeError || undefined
        }, (insight) => this.rishiSubmissionInsight.set(insight));
        this.showEmotionModal.set(!accepted);
        this.isSubmittingLeetCode.set(false);
      },
      error: (err) => {
        const errorMessage = err?.error?.error || err?.error?.details || 'LeetCode submission failed.';
        this.submissionError.set(errorMessage);
        this.recordRishiCompileAttempt({
          source: 'leetcode_submit',
          success: false,
          executionTimeMs: 0,
          submissionStatus: 'SubmissionFailed',
          errorMessage
        }, (insight) => this.rishiSubmissionInsight.set(insight));
        this.isSubmittingLeetCode.set(false);
      }
    });
  }

  clearOutput() {
    this.result.set(null);
    this.rishiRunInsight.set(null);
  }

  clearSubmissionState() {
    this.submissionResponse.set(null);
    this.submissionError.set(null);
    this.rishiSubmissionInsight.set(null);
    this.showEmotionModal.set(false);
  }

  resetCode() {
    this.sourceCode.set(this.boilerplates[this.selectedLanguage()] || this.boilerplates['java']);
    this.result.set(null);
    this.rishiRunInsight.set(null);
    this.submissionResponse.set(null);
    this.submissionError.set(null);
    this.rishiSubmissionInsight.set(null);
    this.showEmotionModal.set(false);
    this.stdinInput.set('');
  }

  recordFailureEmotion(emotion: 'frustrated' | 'neutral' | 'motivated') {
    if (this.isSavingEmotion()) {
      return;
    }

    this.isSavingEmotion.set(true);
    this.compilerService.recordFailureEmotion(emotion).subscribe({
      next: () => {
        this.showEmotionModal.set(false);
        this.isSavingEmotion.set(false);
      },
      error: () => {
        this.isSavingEmotion.set(false);
      }
    });
  }

  dismissEmotionModal() {
    this.showEmotionModal.set(false);
  }

  onInputChange(event: Event) {
    const target = event.target as HTMLTextAreaElement;
    this.stdinInput.set(target.value);
    this.markRishiActivity();
  }

  formatRishiDuration(ms: number): string {
    const safeMs = Math.max(0, Math.floor(ms));
    const totalSeconds = Math.floor(safeMs / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  }

  formatRishiSessionState(state: RishiSessionState): string {
    switch (state) {
      case 'TYPING':
        return 'TYPING';
      case 'CURSOR_IDLE':
        return 'CURSOR IDLE';
      case 'EDITOR_UNFOCUSED':
        return 'EDITOR UNFOCUSED';
      case 'TAB_HIDDEN':
        return 'TAB/WINDOW NOT VISIBLE';
      case 'WAITING':
      default:
        return 'WAITING FOR CODE INPUT';
    }
  }

  private startRishiCodingSession() {
    this.hasUserStartedCoding = false;
    this.isEditorTextFocused = false;
    this.compilerService.startRishiCodingSession({
      language: this.selectedLanguage(),
      problemSlug: this.selectedQuestion()?.slug
    }).subscribe({
      next: (response) => {
        this.rishiCodingSessionId.set(response.sessionId);
        this.telemetrySessionStartMs = Date.now();
        this.lastEditorInteractionMs = this.telemetrySessionStartMs;
        this.lastTypingMs = 0;
        this.sessionActiveDurationMs = 0;
        this.sessionTypingDurationMs = 0;
        this.sessionCursorIdleDurationMs = 0;
        this.sessionEditorUnfocusedDurationMs = 0;
        this.sessionTabHiddenDurationMs = 0;
        this.rishiSessionElapsedMs.set(0);
        this.rishiSessionActiveMs.set(0);
        this.rishiSessionState.set('WAITING');
        this.rishiSessionIdle.set(true);
        this.startRishiTelemetryFlush();
      },
      error: () => {
        this.rishiCodingSessionId.set(null);
      }
    });
  }

  private startRishiTelemetryFlush() {
    if (this.telemetryFlushHandle) {
      clearInterval(this.telemetryFlushHandle);
    }
    this.telemetryFlushHandle = setInterval(() => {
      this.flushRishiCodeChanges();
    }, 10000);
  }

  private startRishiActivityTimer() {
    if (this.activityTimerHandle) {
      clearInterval(this.activityTimerHandle);
    }
    this.activityTimerHandle = setInterval(() => {
      if (!this.telemetrySessionStartMs) {
        return;
      }
      const now = Date.now();
      this.rishiSessionElapsedMs.set(now - this.telemetrySessionStartMs);
      const state = this.deriveRishiSessionState(now);
      this.rishiSessionState.set(state);
      const isSessionActive = state === 'TYPING' || state === 'CURSOR_IDLE';
      this.rishiSessionIdle.set(!isSessionActive);
      switch (state) {
        case 'TYPING':
          this.sessionTypingDurationMs += 1000;
          break;
        case 'CURSOR_IDLE':
          this.sessionCursorIdleDurationMs += 1000;
          break;
        case 'EDITOR_UNFOCUSED':
          this.sessionEditorUnfocusedDurationMs += 1000;
          break;
        case 'TAB_HIDDEN':
          this.sessionTabHiddenDurationMs += 1000;
          break;
        default:
          break;
      }
      if (isSessionActive) {
        this.sessionActiveDurationMs += 1000;
        this.rishiSessionActiveMs.set(this.sessionActiveDurationMs);
      }
    }, 1000);
  }

  private deriveRishiSessionState(now: number): RishiSessionState {
    if (!this.telemetrySessionStartMs || !this.hasUserStartedCoding) {
      return 'WAITING';
    }
    if (!this.isWindowFocused || !this.isDocumentVisible) {
      return 'TAB_HIDDEN';
    }
    if (!this.isEditorTextFocused) {
      return 'EDITOR_UNFOCUSED';
    }
    if (now - this.lastTypingMs <= this.typingRecencyWindowMs) {
      return 'TYPING';
    }
    return 'CURSOR_IDLE';
  }

  private markEditorInteraction(isTyping: boolean) {
    const now = Date.now();
    this.lastEditorInteractionMs = now;
    if (isTyping) {
      this.hasUserStartedCoding = true;
      this.lastTypingMs = now;
    }
    this.updateRishiSessionState(now);
  }

  private markRishiActivity() {
    if (!this.isEditorTextFocused) {
      this.updateRishiSessionState(Date.now());
      return;
    }
    this.markEditorInteraction(false);
  }

  private updateRishiSessionState(now: number) {
    const state = this.deriveRishiSessionState(now);
    this.rishiSessionState.set(state);
    this.rishiSessionIdle.set(!(state === 'TYPING' || state === 'CURSOR_IDLE'));
  }

  private registerRishiPresenceTracking() {
    if (typeof window === 'undefined' || typeof document === 'undefined') {
      return;
    }

    this.isWindowFocused = document.hasFocus();
    this.isDocumentVisible = !document.hidden;

    document.addEventListener('visibilitychange', this.handleVisibilityChange);
    window.addEventListener('blur', this.handleWindowBlur);
    window.addEventListener('focus', this.handleWindowFocus);
  }

  private unregisterRishiPresenceTracking() {
    if (typeof window === 'undefined' || typeof document === 'undefined') {
      return;
    }

    document.removeEventListener('visibilitychange', this.handleVisibilityChange);
    window.removeEventListener('blur', this.handleWindowBlur);
    window.removeEventListener('focus', this.handleWindowFocus);
  }

  private isUserEditorChangeEvent(event: any): boolean {
    if (!event || event.isFlush) {
      return false;
    }
    const changes = Array.isArray(event.changes) ? event.changes : [];
    return changes.some((change: any) => {
      const insertedChars = (change?.text || '').length;
      const deletedChars = change?.rangeLength ?? 0;
      return insertedChars > 0 || deletedChars > 0;
    });
  }

  private flushRishiCodeChanges() {
    const sessionId = this.rishiCodingSessionId();
    if (!sessionId || this.telemetryBuffer.length === 0) {
      return;
    }

    const payload = this.telemetryBuffer.splice(0, this.telemetryBuffer.length);
    this.compilerService.recordRishiCodeChanges(sessionId, { events: payload }).subscribe({
      error: () => {
        const merged = [...payload, ...this.telemetryBuffer];
        this.telemetryBuffer = merged.slice(-400);
      }
    });
  }

  private recordRishiCompileAttempt(
    request: RishiCompileAttemptRequest,
    onInsight?: (insight: RishiCompileAttemptAnalysisResponse) => void
  ) {
    const sessionId = this.rishiCodingSessionId();
    if (!sessionId) {
      return;
    }

    this.compilerService.recordRishiCompileAttempt(sessionId, {
      ...request,
      language: request.language || this.selectedLanguage(),
      problemSlug: request.problemSlug || this.selectedQuestion()?.slug
    }).subscribe({
      next: (insight) => {
        onInsight?.(insight);
      },
      error: () => {
        // Non-blocking telemetry.
      }
    });
  }

  private parseExecutionTimeMs(value: string | null | undefined): number {
    if (!value) {
      return 0;
    }
    const match = value.match(/(\d+)/);
    return match ? Number(match[1]) : 0;
  }

  private extractJudgeCount(
    judge: Record<string, unknown> | undefined,
    keys: string[]
  ): number {
    if (!judge) {
      return -1;
    }
    for (const key of keys) {
      const value = judge[key];
      if (typeof value === 'number' && Number.isFinite(value)) {
        return Math.max(0, Math.floor(value));
      }
      if (typeof value === 'string' && value.trim()) {
        const parsed = Number(value.trim());
        if (Number.isFinite(parsed)) {
          return Math.max(0, Math.floor(parsed));
        }
      }
    }
    return -1;
  }

  private extractJudgeText(
    judge: Record<string, unknown> | undefined,
    keys: string[]
  ): string {
    if (!judge) {
      return '';
    }
    for (const key of keys) {
      const value = judge[key];
      if (typeof value === 'string' && value.trim()) {
        return value.trim();
      }
    }
    return '';
  }

  private endRishiCodingSession(reason: string) {
    const sessionId = this.rishiCodingSessionId();
    if (!sessionId) {
      return;
    }
    this.rishiCodingSessionId.set(null);

    this.flushRishiCodeChanges();

    this.compilerService.endRishiCodingSession(sessionId, {
      reason,
      activeDurationMs: Math.max(0, Math.round(this.sessionActiveDurationMs)),
      typingDurationMs: Math.max(0, Math.round(this.sessionTypingDurationMs)),
      cursorIdleDurationMs: Math.max(0, Math.round(this.sessionCursorIdleDurationMs)),
      editorUnfocusedDurationMs: Math.max(0, Math.round(this.sessionEditorUnfocusedDurationMs)),
      tabHiddenDurationMs: Math.max(0, Math.round(this.sessionTabHiddenDurationMs))
    }).subscribe({
      error: () => {
        // Non-blocking telemetry.
      }
    });
    this.rishiSessionState.set('WAITING');
    this.rishiSessionIdle.set(true);
  }

  getLanguageIcon(command: string): string {
    switch (command) {
      case 'java':
        return 'Coffee';
      case 'python':
        return 'Code2';
      case 'cpp':
      case 'c++':
        return 'Zap';
      case 'javascript':
      case 'js':
        return 'FileJson';
      default:
        return 'FileCode';
    }
  }

  getJudgeField(field: string): string {
    const judgeResult = this.submissionResponse()?.judgeResult;
    if (!judgeResult) {
      return '';
    }

    const value = judgeResult[field];
    return value == null ? '' : String(value);
  }

  private deduplicateLanguages(languages: CompilerInfo[]): CompilerInfo[] {
    const deduped = new Map<string, CompilerInfo>();

    for (const language of languages) {
      const normalizedCommand = this.normalizeLanguageCommand(language.command);
      if (!normalizedCommand || deduped.has(normalizedCommand)) {
        continue;
      }

      deduped.set(normalizedCommand, {
        ...language,
        command: normalizedCommand,
        languageName: this.normalizeLanguageName(language.languageName, normalizedCommand)
      });
    }

    return Array.from(deduped.values());
  }

  private normalizeLanguageCommand(command: string): string {
    const normalized = (command || '').trim().toLowerCase();
    if (normalized === 'c++') {
      return 'cpp';
    }
    if (normalized === 'js') {
      return 'javascript';
    }
    return normalized;
  }

  private normalizeLanguageName(languageName: string, command: string): string {
    switch (command) {
      case 'java':
        return 'Java';
      case 'python':
        return 'Python';
      case 'cpp':
        return 'C++';
      case 'javascript':
        return 'JavaScript';
      default:
        return languageName;
    }
  }

  private extractSlug(url: string): string {
    const match = url.match(/leetcode\.com\/problems\/([^/?#]+)/i);
    return match?.[1] || '';
  }

  private toLeetCodeUrl(slug: string): string {
    return slug ? `https://leetcode.com/problems/${slug}/` : '';
  }

  private parseTags(tagsParam: string): string[] {
    return tagsParam
      .split(',')
      .map((tag) => tag.trim())
      .filter((tag) => !!tag);
  }

  private humanizeSlug(slug: string): string {
    return slug
      .split('-')
      .filter((part) => part)
      .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
      .join(' ');
  }

  private isAcceptedStatus(status: string | undefined): boolean {
    return (status || '').trim().toLowerCase() === 'accepted';
  }

  private isSameQuestion(
    left: Partial<LeetCodeQuestion> | null,
    right: Partial<LeetCodeQuestion> | null
  ): boolean {
    if (!left || !right) {
      return false;
    }

    const leftSlug = (left.slug || '').trim().toLowerCase();
    const rightSlug = (right.slug || '').trim().toLowerCase();
    if (leftSlug && rightSlug && leftSlug === rightSlug) {
      return true;
    }

    const leftUrl = (left.url || '').trim().toLowerCase();
    const rightUrl = (right.url || '').trim().toLowerCase();
    if (leftUrl && rightUrl && leftUrl === rightUrl) {
      return true;
    }

    const leftTitle = (left.title || '').trim().toLowerCase();
    const rightTitle = (right.title || '').trim().toLowerCase();
    return !!leftTitle && !!rightTitle && leftTitle === rightTitle;
  }

  private extractQuestionContent(response: any): string | null {
    const contentCandidates = [
      response?.data?.question?.content,
      response?.data?.question?.translatedContent,
      response?.question?.content,
      response?.question?.translatedContent,
      response?.content
    ];

    for (const candidate of contentCandidates) {
      if (typeof candidate === 'string' && candidate.trim()) {
        return candidate;
      }
    }

    return null;
  }


}
