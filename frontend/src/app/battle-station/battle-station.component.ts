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
  private questionDetailRequestToken = 0;

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
    this.registerRouteQuestionSync();
    this.loadAvailableLanguages();
    this.loadQuestions('all');
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
        const accepted = this.isAcceptedStatus(response?.status);
        this.showEmotionModal.set(!accepted);
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
    this.showEmotionModal.set(false);
  }

  resetCode() {
    this.sourceCode.set(this.boilerplates[this.selectedLanguage()] || this.boilerplates['java']);
    this.result.set(null);
    this.submissionResponse.set(null);
    this.submissionError.set(null);
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
