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
  LeetCodeSubmissionResponse
} from '../core/compiler.service';
import { LeetCodeQuestion, QuestionBankService } from '../core/question-bank.service';

export type StressMode = 'NORMAL' | 'ONE_SHOT' | 'BLITZ' | 'BLINDFOLD' | 'IRON_MAN';
export type CognitiveTrack = 'HEURISTIC_FLOW' | 'SYSTEM2_OVERRIDE' | 'FAULT_TOLERANCE' | 'AFFECTIVE_REGULATION';

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



  // Question state
  isQuestionLoading = signal(false);
  questionLoadError = signal<string | null>(null);
  questions = signal<LeetCodeQuestion[]>([]);
  questionSearch = signal('');
  selectedQuestion = signal<LeetCodeQuestion | null>(null);
  questionHtml = signal<SafeHtml | null>(null);
  filteredQuestions = computed(() => {
    const search = this.questionSearch().trim().toLowerCase();
    const allQuestions = this.questions();

    if (!search) {
      return allQuestions.slice(0, 200);
    }

    return allQuestions
      .filter((question) =>
        question.title.toLowerCase().includes(search) ||
        question.slug?.toLowerCase().includes(search) ||
        question.tags.some((tag) => tag.toLowerCase().includes(search))
      )
      .slice(0, 200);
  });

  // LeetCode auth state (stored securely on backend)
  isLeetCodeConnected = signal(false);
  isLeetCodeAuthLoading = signal(false);

  // LeetCode submission state
  isSubmittingLeetCode = signal(false);
  submissionResponse = signal<LeetCodeSubmissionResponse | null>(null);
  submissionError = signal<string | null>(null);
  canSubmitToLeetCode = computed(() => {
    return Boolean(
      this.selectedQuestion()?.slug &&
        this.sourceCode().trim() &&
        this.isLeetCodeConnected() &&
        !this.isSubmittingLeetCode()
    );
  });
  submitGateReason = computed(() => {
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
    this.registerRouteQuestionSync();
    this.loadAvailableLanguages();
    this.loadQuestions();
    this.loadLeetCodeAuthStatus();
    this.sourceCode.set(this.boilerplates['java']);
  }

  toggleSetupPanel() {
    this.isSetupPanelCollapsed.update((current) => !current);
  }

  ngOnDestroy() {
    this.routeSubscription?.unsubscribe();
  }

  private loadAvailableLanguages() {
    this.compilerService.getAvailableLanguages().subscribe({
      next: (languages) => {
        this.availableLanguages.set(languages);
        if (!languages.length) {
          this.selectLanguage('java');
          return;
        }

        const current = this.selectedLanguage();
        const active = languages.find((lang) => lang.command === current);
        this.selectLanguage(active?.command || languages[0].command);
      },
      error: () => {
        this.selectLanguage('java');
      }
    });
  }

  private loadQuestions() {
    this.isQuestionLoading.set(true);
    this.questionLoadError.set(null);

    this.questionBankService.getAllQuestions().subscribe({
      next: (questions) => {
        const normalized = questions.map((question) => this.normalizeQuestion(question));
        this.questions.set(normalized);
        this.isQuestionLoading.set(false);

        this.syncSelectedQuestionWithLoadedData();

        if (!this.selectedQuestion() && normalized.length > 0) {
          this.selectQuestion(normalized[0], false);
        }
      },
      error: () => {
        this.isQuestionLoading.set(false);
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

      this.selectedQuestion.set(question);
      this.syncSelectedQuestionWithLoadedData();
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
    this.selectedLanguage.set(lang);
    this.editorOptions = { ...this.editorOptions, language: lang };

    const currentCode = this.sourceCode();
    const isBoilerplate = Object.values(this.boilerplates).some((snippet) => snippet === currentCode);
    if (!currentCode.trim() || isBoilerplate) {
      this.sourceCode.set(this.boilerplates[lang] || this.boilerplates['java']);
    }
  }

  onCodeChange(nextCode: string) {
    this.sourceCode.set(nextCode || '');
  }

  onQuestionSearchChange(event: Event) {
    const target = event.target as HTMLInputElement;
    this.questionSearch.set(target.value || '');
  }

  selectQuestion(question: LeetCodeQuestion, updateRoute = true) {
    const normalized = this.normalizeQuestion(question);
    this.selectedQuestion.set(normalized);
    this.submissionResponse.set(null);
    this.submissionError.set(null);
    this.questionHtml.set(null);

    if (normalized.slug) {
      this.isQuestionLoading.set(true);
      this.compilerService.getLeetCodeQuestionDetails(normalized.slug).subscribe({
        next: (res) => {
          const content = res?.data?.question?.content;
          if (content) {
             this.questionHtml.set(this.sanitizer.bypassSecurityTrustHtml(content));
          } else {
             this.questionHtml.set(this.sanitizer.bypassSecurityTrustHtml(`<p>Description unavailable via API. <a href="\${normalized.url}" target="_blank" class="text-indigo-400">View on LeetCode</a></p>`));
          }
          this.isQuestionLoading.set(false);
        },
        error: (err) => {
          this.questionHtml.set(this.sanitizer.bypassSecurityTrustHtml(`<p class="text-crimson-400">Failed to load problem description. <a href="\${normalized.url}" target="_blank" class="text-indigo-400 underline">View on LeetCode</a></p>`));
          this.isQuestionLoading.set(false);
        }
      });
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
    if (this.isExecuting() || !this.sourceCode().trim()) {
      return;
    }

    this.isExecuting.set(true);
    this.result.set(null);

    this.compilerService.executeCode({
      sourceCode: this.sourceCode(),
      language: this.selectedLanguage(),
      input: this.stdinInput(),
      timeoutSeconds: this.timeoutSeconds(),
      problemSlug: this.selectedQuestion()?.slug
    }).subscribe({
      next: (executionResult) => {
        this.result.set(executionResult);
        this.isExecuting.set(false);
      },
      error: (err) => {
        this.result.set({
          success: false,
          output: '',
          error: err?.error?.error || 'Execution failed. Compiler service unavailable.',
          executionTime: '0ms',
          language: this.selectedLanguage(),
          timestamp: new Date().toISOString()
        });
        this.isExecuting.set(false);
      }
    });
  }

  submitToLeetCode() {
    if (this.isSubmittingLeetCode()) {
      return;
    }

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

    this.compilerService.submitToLeetCode({
      sourceCode: this.sourceCode(),
      language: this.selectedLanguage(),
      problemSlug: question.slug,
      waitForResult: true
    }).subscribe({
      next: (response) => {
        this.submissionResponse.set(response);
        this.isSubmittingLeetCode.set(false);
      },
      error: (err) => {
        const errorMessage = err?.error?.error || err?.error?.details || 'LeetCode submission failed.';
        this.submissionError.set(errorMessage);
        this.isSubmittingLeetCode.set(false);
      }
    });
  }

  clearOutput() {
    this.result.set(null);
  }

  clearSubmissionState() {
    this.submissionResponse.set(null);
    this.submissionError.set(null);
  }

  resetCode() {
    this.sourceCode.set(this.boilerplates[this.selectedLanguage()] || this.boilerplates['java']);
    this.result.set(null);
    this.submissionResponse.set(null);
    this.submissionError.set(null);
    this.stdinInput.set('');
  }

  onInputChange(event: Event) {
    const target = event.target as HTMLTextAreaElement;
    this.stdinInput.set(target.value);
  }

  getLanguageIcon(command: string): string {
    switch (command) {
      case 'java':
        return '☕';
      case 'python':
        return '🐍';
      case 'cpp':
      case 'c++':
        return '⚡';
      case 'javascript':
      case 'js':
        return '📜';
      default:
        return '📝';
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


}
