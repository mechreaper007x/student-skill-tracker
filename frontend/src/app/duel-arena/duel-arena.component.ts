import { CommonModule } from '@angular/common';
import { Component, computed, effect, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { MonacoEditorModule } from 'ngx-monaco-editor-v2';
import { Subject } from 'rxjs';
import { debounceTime, takeUntil } from 'rxjs/operators';
import { AuthService } from '../core/auth/auth.service';
import { CompilationResult, CompilerService } from '../core/compiler.service';
import {
    AnswerResult,
    MatchStartPayload,
    RoundData,
    WebSocketService
} from '../core/websocket.service';

export type DuelPhase = 'lobby' | 'matchmaking' | 'dueling' | 'round-transition' | 'finished';
type DuelLanguage = 'java' | 'python' | 'cpp' | 'javascript';

@Component({
  selector: 'app-duel-arena',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule, MonacoEditorModule],
  templateUrl: './duel-arena.component.html',
  styleUrl: './duel-arena.component.css'
})
export class DuelArenaComponent implements OnInit, OnDestroy {
  private wsService = inject(WebSocketService);
  private compilerService = inject(CompilerService);
  private authService = inject(AuthService);
  private router = inject(Router);
  private destroy$ = new Subject<void>();
  private typingSubject = new Subject<string>();

  // Phase
  phase = signal<DuelPhase>('lobby');

  // User info
  currentUser = computed(() => this.authService.currentUser()?.name || 'You');
  currentUserMatchmakingId = computed(
    () => this.authService.currentUser()?.email || this.authService.currentUser()?.name || ''
  );

  // Match state
  sessionId = signal('');
  opponent = signal('');
  allRounds = signal<RoundData[]>([]);
  currentRound = signal(1);
  currentRoundData = computed(() => {
    const rounds = this.allRounds();
    const roundNumber = this.currentRound();
    if (!rounds.length) return null;

    const byRoundNumber = rounds.find(r => Number((r as any).round) === roundNumber);
    return byRoundNumber || rounds[roundNumber - 1] || null;
  });

  // Scores
  player1Score = signal(0);
  player2Score = signal(0);
  player1Name = signal('');
  player2Name = signal('');
  myScore = computed(() => this.currentUserMatchmakingId() === this.player1Name() ? this.player1Score() : this.player2Score());
  opponentScoreValue = computed(() => this.currentUserMatchmakingId() === this.player1Name() ? this.player2Score() : this.player1Score());

  // Round Timer
  roundTimerSeconds = signal(0);
  private roundTimerInterval: ReturnType<typeof setInterval> | null = null;
  formattedRoundTimer = computed(() => {
    const s = this.roundTimerSeconds();
    const mins = Math.floor(s / 60);
    const secs = s % 60;
    return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  });

  // MCQ state
  activeMcqIndex = signal(0);
  mcqAnswers = signal<number[]>([]);
  mcqSubmitted = signal(false);

  // Memory state
  memoryCards = signal<Array<{ id: number; text: string; type: 'content' | 'match'; pairId: number; flipped: boolean; matched: boolean }>>([]);
  memoryFirstCard = signal<number | null>(null);
  memoryMatchCount = signal(0);
  memorySubmitted = signal(false);

  // Puzzle / Problem Solving state
  textAnswer = signal('');
  textSubmitted = signal(false);

  // Coding state
  myCode = signal('');
  opponentCode = signal('');
  selectedLanguage = signal<DuelLanguage>('java');
  isExecuting = signal(false);
  myResult = signal<CompilationResult | null>(null);
  codingSubmitted = signal(false);

  // Answer result from server
  lastAnswerResult = signal<AnswerResult | null>(null);

  // Finished state
  winner = signal('');

  // Editor configs
  myEditorOptions = {
    theme: 'vs-dark', language: 'java', automaticLayout: true,
    minimap: { enabled: false }, fontSize: 13,
    lineNumbers: 'on' as const, scrollBeyondLastLine: false, wordWrap: 'on' as const,
  };
  opponentEditorOptions = {
    theme: 'vs-dark', language: 'java', automaticLayout: true,
    minimap: { enabled: false }, fontSize: 13,
    lineNumbers: 'on' as const, readOnly: true, scrollBeyondLastLine: false, wordWrap: 'on' as const,
  };
  private readonly fallbackStarterCode: Record<DuelLanguage, string> = {
    java: 'class Solution {\\n  public int solve(int[] nums) {\\n    int sum = 0;\\n    for (int n : nums) {\\n      sum += n;\\n    }\\n    return sum;\\n  }\\n}',
    python: 'def solve(nums):\\n    return sum(nums)',
    cpp: '#include <vector>\\nusing namespace std;\\n\\nint solve(vector<int> nums) {\\n  int sum = 0;\\n  for (int n : nums) {\\n    sum += n;\\n  }\\n  return sum;\\n}',
    javascript: 'function solve(nums) {\\n  return nums.reduce((sum, n) => sum + n, 0);\\n}',
  };

  wsConnected = this.wsService.connected;

  constructor() {
    // Match start
    effect(() => {
      const match = this.wsService.matchStart();
      if (match && (this.phase() === 'matchmaking')) {
        this.onMatchStart(match);
      }
    });

    // Opponent typing (for coding round)
    effect(() => {
      const typing = this.wsService.opponentTyping();
      if (typing && typing.username !== this.currentUserMatchmakingId()) {
        this.opponentCode.set(typing.codeDelta || '');
      }
    });

    // Answer result
    effect(() => {
      const result = this.wsService.answerResult();
      if (result) {
        this.lastAnswerResult.set(result);
        this.player1Score.set(result.player1Score);
        this.player2Score.set(result.player2Score);
      }
    });

    // Round advance
    effect(() => {
      const advance = this.wsService.roundAdvance();
      if (advance) {
        this.player1Score.set(advance.player1Score);
        this.player2Score.set(advance.player2Score);
        if (advance.status === 'FINISHED') {
          this.winner.set(advance.winner || 'DRAW');
          this.phase.set('finished');
          this.stopRoundTimer();
        } else if (advance.status === 'NEXT_ROUND' && advance.currentRound) {
          this.currentRound.set(advance.currentRound);
          this.phase.set('round-transition');
          this.stopRoundTimer();
          // Auto-transition to dueling after 3 seconds
          setTimeout(() => {
            this.startRound(advance.currentRound!);
          }, 3000);
        }
      }
    });
  }

  ngOnInit() {
    this.typingSubject.pipe(
      debounceTime(300),
      takeUntil(this.destroy$)
    ).subscribe(code => {
      if (this.sessionId()) {
        this.wsService.sendTypingDelta(this.sessionId(), this.currentUserMatchmakingId(), code);
      }
    });
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
    this.stopRoundTimer();
    if (this.phase() === 'matchmaking') {
      this.wsService.leaveLobby().subscribe();
    }
    this.clearRealtimeSignals();
  }

  // --- Lobby ---
  findMatch() {
    const matchmakingId = this.currentUserMatchmakingId();
    if (!matchmakingId) {
      this.phase.set('lobby');
      return;
    }
    this.clearRealtimeSignals();
    this.phase.set('matchmaking');
    this.wsService.subscribeToMatchmaking(matchmakingId).then(() => {
      this.wsService.joinLobby().subscribe({
        error: () => this.phase.set('lobby')
      });
    });
  }

  cancelMatchmaking() {
    this.wsService.leaveLobby().subscribe();
    this.clearRealtimeSignals();
    this.phase.set('lobby');
  }

  // --- Match Start ---
  private onMatchStart(match: MatchStartPayload) {
    this.sessionId.set(match.sessionId);
    this.player1Name.set(match.player1);
    this.player2Name.set(match.player2);
    this.opponent.set(match.player1 === this.currentUserMatchmakingId() ? match.player2 : match.player1);

    // Parse rounds
    let rounds: RoundData[] = [];
    try {
      rounds = this.normalizeRounds(JSON.parse(match.puzzle.roundsJson || '[]'));
    } catch (e) {
      console.error('Failed to parse rounds JSON', e);
    }

    if (!rounds.length) {
      console.warn('No usable rounds in duel payload. Falling back to emergency round set.');
      rounds = this.getEmergencyFallbackRounds();
    }

    this.allRounds.set(rounds);
    this.startRound(1);
  }

  private startRound(roundNumber: number) {
    this.currentRound.set(roundNumber);
    this.phase.set('dueling');
    this.lastAnswerResult.set(null);

    const round = this.currentRoundData();
    if (!round) return;

    // Reset round-specific state
    this.activeMcqIndex.set(0);
    this.mcqAnswers.set(round.questions ? new Array(round.questions.length).fill(-1) : []);
    this.mcqSubmitted.set(false);
    this.textAnswer.set('');
    this.textSubmitted.set(false);
    this.codingSubmitted.set(false);
    this.myResult.set(null);
    this.opponentCode.set('');

    // Memory round: shuffle cards
    if (round.type === 'MEMORY' && round.pairs) {
      const cards: Array<{ id: number; text: string; type: 'content' | 'match'; pairId: number; flipped: boolean; matched: boolean }> = [];
      for (const pair of round.pairs) {
        cards.push({ id: pair.id * 2, text: pair.content, type: 'content', pairId: pair.id, flipped: false, matched: false });
        cards.push({ id: pair.id * 2 + 1, text: pair.match, type: 'match', pairId: pair.id, flipped: false, matched: false });
      }
      // Shuffle
      for (let i = cards.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [cards[i], cards[j]] = [cards[j], cards[i]];
      }
      this.memoryCards.set(cards);
      this.memoryFirstCard.set(null);
      this.memoryMatchCount.set(0);
      this.memorySubmitted.set(false);
    }

    // Coding round: set starter code
    if (round.type === 'CODING') {
      const lang = this.selectedLanguage();
      this.myCode.set(this.getStarterCodeForLanguage(round.starterCode, lang));
    }

    // Start timer
    this.startRoundTimer(round.timeLimitSeconds || 120);
  }

  // --- Timer ---
  private startRoundTimer(seconds: number) {
    this.roundTimerSeconds.set(seconds);
    this.stopRoundTimer();
    this.roundTimerInterval = setInterval(() => {
      this.roundTimerSeconds.update(t => {
        if (t <= 1) {
          this.onTimerExpired();
          return 0;
        }
        return t - 1;
      });
    }, 1000);
  }

  private stopRoundTimer() {
    if (this.roundTimerInterval) {
      clearInterval(this.roundTimerInterval);
      this.roundTimerInterval = null;
    }
  }

  private onTimerExpired() {
    this.stopRoundTimer();
    // Auto-submit whatever they have
    const round = this.currentRoundData();
    if (!round) return;

    switch (round.type) {
      case 'MCQ':
        if (!this.mcqSubmitted()) this.submitMcq();
        break;
      case 'MEMORY':
        if (!this.memorySubmitted()) this.submitMemory();
        break;
      case 'PUZZLE':
      case 'PROBLEM_SOLVING':
        if (!this.textSubmitted()) this.submitTextAnswer();
        break;
      case 'CODING':
        if (!this.codingSubmitted()) this.submitCoding();
        break;
    }
  }

  // --- MCQ ---
  selectMcqAnswer(questionIndex: number, optionIndex: number) {
    if (this.mcqSubmitted()) return;
    this.mcqAnswers.update(answers => {
      const copy = [...answers];
      copy[questionIndex] = optionIndex;
      return copy;
    });

    // Auto-advance to next question after short delay
    const totalQuestions = this.currentRoundData()?.questions?.length || 0;
    setTimeout(() => {
      if (this.activeMcqIndex() < totalQuestions - 1) {
        this.activeMcqIndex.update(idx => idx + 1);
      } else {
        this.submitMcq();
      }
    }, 300);
  }

  prevMcq() {
    this.activeMcqIndex.update(i => i > 0 ? i - 1 : i);
  }

  submitMcq() {
    this.mcqSubmitted.set(true);
    const answer = this.mcqAnswers().join(',');
    this.wsService.submitAnswer(this.sessionId(), this.currentUserMatchmakingId(), answer);
    // Request round advance after a short delay
    setTimeout(() => {
      this.wsService.advanceRound(this.sessionId(), this.currentUserMatchmakingId());
    }, 2000);
  }

  // --- Memory ---
  flipMemoryCard(index: number) {
    if (this.memorySubmitted()) return;
    const cards = [...this.memoryCards()];
    const card = cards[index];
    if (card.flipped || card.matched) return;

    const firstIdx = this.memoryFirstCard();
    if (firstIdx === null) {
      // First card of pair
      cards[index] = { ...card, flipped: true };
      this.memoryCards.set(cards);
      this.memoryFirstCard.set(index);
    } else {
      // Second card
      cards[index] = { ...card, flipped: true };
      this.memoryCards.set(cards);
      this.memoryFirstCard.set(null);

      const firstCard = cards[firstIdx];
      if (firstCard.pairId === card.pairId && firstCard.type !== card.type) {
        // Match!
        setTimeout(() => {
          const updated = [...this.memoryCards()];
          updated[firstIdx] = { ...updated[firstIdx], matched: true };
          updated[index] = { ...updated[index], matched: true };
          this.memoryCards.set(updated);
          this.memoryMatchCount.update(c => c + 1);

          // Check if all matched
          const round = this.currentRoundData();
          if (round?.pairs && this.memoryMatchCount() + 1 >= round.pairs.length) {
            this.submitMemory();
          }
        }, 500);
      } else {
        // No match — flip back after delay
        setTimeout(() => {
          const updated = [...this.memoryCards()];
          updated[firstIdx] = { ...updated[firstIdx], flipped: false };
          updated[index] = { ...updated[index], flipped: false };
          this.memoryCards.set(updated);
        }, 800);
      }
    }
  }

  submitMemory() {
    this.memorySubmitted.set(true);
    this.wsService.submitAnswer(this.sessionId(), this.currentUserMatchmakingId(), String(this.memoryMatchCount()));
    setTimeout(() => {
      this.wsService.advanceRound(this.sessionId(), this.currentUserMatchmakingId());
    }, 2000);
  }

  // --- Puzzle / Problem Solving ---
  submitTextAnswer() {
    this.textSubmitted.set(true);
    this.wsService.submitAnswer(this.sessionId(), this.currentUserMatchmakingId(), this.textAnswer());
    setTimeout(() => {
      this.wsService.advanceRound(this.sessionId(), this.currentUserMatchmakingId());
    }, 2000);
  }

  // --- Coding ---
  onMyCodeChange(code: string) {
    this.myCode.set(code);
    this.typingSubject.next(code);
  }

  selectLanguage(lang: string) {
    const nextLanguage = this.normalizeLanguage(lang);
    this.selectedLanguage.set(nextLanguage);
    this.myEditorOptions = { ...this.myEditorOptions, language: nextLanguage };
    this.opponentEditorOptions = { ...this.opponentEditorOptions, language: nextLanguage };
    const round = this.currentRoundData();
    if (round?.type === 'CODING') {
      this.myCode.set(this.getStarterCodeForLanguage(round.starterCode, nextLanguage));
    }
  }

  runCode() {
    if (this.isExecuting() || !this.myCode().trim()) return;
    this.isExecuting.set(true);
    this.myResult.set(null);

    this.compilerService.executeCode({
      sourceCode: this.myCode(),
      language: this.selectedLanguage(),
      input: '',
      timeoutSeconds: 10,
      problemSlug: undefined
    }).subscribe({
      next: (result) => {
        this.myResult.set(result);
        this.isExecuting.set(false);
      },
      error: (err) => {
        this.myResult.set({
          success: false, output: '',
          error: err?.error?.error || 'Execution failed.',
          executionTime: '0ms', language: this.selectedLanguage(),
          timestamp: new Date().toISOString()
        });
        this.isExecuting.set(false);
      }
    });
  }

  submitCoding() {
    this.codingSubmitted.set(true);
    const passed = this.myResult()?.success ? 1 : 0;
    this.wsService.submitAnswer(this.sessionId(), this.currentUserMatchmakingId(), String(passed));
    this.wsService.sendExecuteStatus(this.sessionId(), this.currentUserMatchmakingId(), passed ? 'PASSED' : 'FAILED', passed, 1);
    setTimeout(() => {
      this.wsService.advanceRound(this.sessionId(), this.currentUserMatchmakingId());
    }, 2000);
  }

  returnToLobby() {
    this.stopRoundTimer();
    this.sessionId.set('');
    this.opponent.set('');
    this.allRounds.set([]);
    this.currentRound.set(1);
    this.player1Score.set(0);
    this.player2Score.set(0);
    this.player1Name.set('');
    this.player2Name.set('');
    this.lastAnswerResult.set(null);
    this.winner.set('');
    this.clearRealtimeSignals();
    this.phase.set('lobby');
  }

  askCornerMan() {
    const rounds = this.allRounds();
    const myMatchId = this.currentUserMatchmakingId();
    const isWinner = this.winner() === myMatchId;
    
    let contextStr = `I just finished a coding duel in the Arena. I ${isWinner ? 'won' : 'lost'} against ${this.opponent()}.\n`;
    contextStr += `Final Score: Me (${this.myScore()}) - Opponent (${this.opponentScoreValue()})\n\n`;

    const codingRound = rounds.find((r: any) => r.type === 'CODING');
    if (codingRound) {
      contextStr += `During the Coding round, I wrote this code in ${this.selectedLanguage()}:\n\`\`\`${this.selectedLanguage()}\n${this.myCode()}\n\`\`\`\n`;
      if (this.opponentCode()) {
        contextStr += `\nMy opponent wrote this code:\n\`\`\`\n${this.opponentCode()}\n\`\`\`\n`;
      }
    }

    contextStr += '\nAct as my post-match Corner Man (Boxing Coach/Senior Mentor). Review the match tape. ';
    if (isWinner) {
      contextStr += 'Tell me what I did well, but don\'t let me get arrogant. Point out inefficiencies where my opponent might have beaten me in time/space complexity if they were faster.';
    } else {
      contextStr += 'Tell me why I lost. Compare my code to my opponent\'s code if available. Be brutally honest but encouraging. What mental model or data structure should I have used to win?';
    }

    sessionStorage.setItem('rishi_pending_context', contextStr);
    this.router.navigate(['/advisor']);
  }

  private clearRealtimeSignals() {
    this.wsService.matchStart.set(null);
    this.wsService.opponentTyping.set(null);
    this.wsService.opponentStatus.set(null);
    this.wsService.answerResult.set(null);
    this.wsService.roundAdvance.set(null);
  }

  private normalizeRounds(raw: unknown): RoundData[] {
    if (!Array.isArray(raw)) return [];

    return raw.map((item: any, idx) => {
      const roundNumber = Number(item?.round) || (idx + 1);
      const type = this.normalizeRoundType(item?.type);
      const normalizedRound = {
        ...item,
        round: roundNumber,
        type,
        bloomLevel: Number(item?.bloomLevel) || this.defaultBloomLevel(roundNumber),
        bloomLevelName: String(item?.bloomLevelName || '').trim() || this.defaultBloomLevelName(roundNumber),
        timeLimitSeconds: Number(item?.timeLimitSeconds) || 120,
      } as RoundData;

      if (type === 'CODING') {
        normalizedRound.starterCode = this.normalizeStarterCodeMap(item?.starterCode);
      }

      return normalizedRound;
    });
  }

  private normalizeRoundType(type: unknown): RoundData['type'] {
    const normalized = String(type || '').trim().toUpperCase().replace(/[\s-]+/g, '_');
    switch (normalized) {
      case 'MCQ':
      case 'QUIZ':
        return 'MCQ';
      case 'MEMORY':
      case 'MEMORY_MATCH':
      case 'MATCHING':
        return 'MEMORY';
      case 'PUZZLE':
        return 'PUZZLE';
      case 'PROBLEM_SOLVING':
      case 'PROBLEM_SOLVING_ROUND':
      case 'PROBLEM_SOLVING_QUESTION':
      case 'PROBLEM':
      case 'LOGIC':
        return 'PROBLEM_SOLVING';
      case 'CODING':
      case 'CODE':
        return 'CODING';
      default:
        return 'MCQ';
    }
  }

  private normalizeStarterCodeMap(starterCode: unknown): RoundData['starterCode'] {
    const source = starterCode && typeof starterCode === 'object'
      ? starterCode as Record<string, unknown>
      : {};

    return {
      java: this.normalizeStarterCodeCandidate(source['java'], 'java'),
      python: this.normalizeStarterCodeCandidate(source['python'], 'python'),
      cpp: this.normalizeStarterCodeCandidate(source['cpp'], 'cpp'),
      javascript: this.normalizeStarterCodeCandidate(source['javascript'], 'javascript'),
    };
  }

  private normalizeStarterCodeCandidate(code: unknown, language: DuelLanguage): string {
    const candidate = typeof code === 'string' ? code.trim() : '';
    if (!candidate || this.isPlaceholderStarterCode(candidate)) {
      return this.fallbackStarterCode[language];
    }
    return candidate;
  }

  private getStarterCodeForLanguage(starterCode: RoundData['starterCode'] | undefined, language: DuelLanguage): string {
    if (!starterCode) {
      return this.fallbackStarterCode[language];
    }
    return this.normalizeStarterCodeCandidate((starterCode as any)[language], language);
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

  private normalizeLanguage(language: string): DuelLanguage {
    switch (language) {
      case 'python':
      case 'cpp':
      case 'javascript':
      case 'java':
        return language;
      default:
        return 'java';
    }
  }

  private getEmergencyFallbackRounds(): RoundData[] {
    return [
      {
        round: 1,
        type: 'MCQ',
        title: 'Quick Fire Quiz',
        bloomLevel: 2,
        bloomLevelName: 'Remember & Understand',
        timeLimitSeconds: 120,
        questions: [
          {
            question: 'What is the time complexity of accessing an array element by index?',
            options: ['O(1)', 'O(n)', 'O(log n)', 'O(n²)'],
            correctIndex: 0,
          },
          {
            question: 'Which data structure uses FIFO ordering?',
            options: ['Stack', 'Queue', 'Tree', 'Graph'],
            correctIndex: 1,
          },
        ],
      },
      {
        round: 2,
        type: 'MEMORY',
        title: 'Memory Match',
        bloomLevel: 3,
        bloomLevelName: 'Understand & Apply',
        timeLimitSeconds: 120,
        gridSize: 4,
        pairs: [
          { id: 0, content: 'HashMap', match: 'O(1) lookup' },
          { id: 1, content: 'BFS', match: 'Queue' },
          { id: 2, content: 'DFS', match: 'Stack' },
          { id: 3, content: 'Binary Search', match: 'O(log n)' },
        ],
      },
      {
        round: 3,
        type: 'PUZZLE',
        title: 'Pattern Decode',
        bloomLevel: 4,
        bloomLevelName: 'Analyze',
        timeLimitSeconds: 180,
        puzzleHtml:
          '<p>Find the next number in the sequence: <b>2, 6, 12, 20, 30, ?</b></p><p>Hint: differences increase by 2.</p>',
        answer: '42',
      },
      {
        round: 4,
        type: 'PROBLEM_SOLVING',
        title: 'Logic Gate',
        bloomLevel: 5,
        bloomLevelName: 'Evaluate',
        timeLimitSeconds: 180,
        problemHtml:
          '<p>A farmer has <b>17 sheep</b>. All but <b>9</b> run away. How many are left?</p>',
        answer: '9',
      },
      {
        round: 5,
        type: 'CODING',
        title: 'Echo Sum',
        bloomLevel: 6,
        bloomLevelName: 'Apply & Create',
        timeLimitSeconds: 600,
        descriptionHtml:
          '<p><b>Task:</b> Given an integer array <code>nums</code>, return the sum of all values.</p>',
        starterCode: {
          java: 'class Solution {\\n  public int solve(int[] nums) {\\n    return 0;\\n  }\\n}',
          python: 'def solve(nums):\\n    return 0',
          cpp: '#include <vector>\\nusing namespace std;\\n\\nint solve(vector<int> nums) {\\n  return 0;\\n}',
          javascript: 'function solve(nums) {\\n  return 0;\\n}',
        },
      },
    ];
  }

  // --- Helpers ---
  getRoundIcon(type: string): string {
    switch (type) {
      case 'MCQ': return '❓';
      case 'MEMORY': return '🧠';
      case 'PUZZLE': return '🧩';
      case 'PROBLEM_SOLVING': return '💡';
      case 'CODING': return '💻';
      default: return '⚡';
    }
  }

  getRoundLabel(type: string): string {
    switch (type) {
      case 'MCQ': return 'QUIZ';
      case 'MEMORY': return 'MEMORY';
      case 'PUZZLE': return 'PUZZLE';
      case 'PROBLEM_SOLVING': return 'PROBLEM SOLVING';
      case 'CODING': return 'CODING';
      default: return type;
    }
  }

  getBloomChip(round: RoundData | null): string {
    if (!round) return '';
    const level = round.bloomLevel || this.defaultBloomLevel(round.round);
    const name = round.bloomLevelName || this.defaultBloomLevelName(round.round);
    return `Bloom L${level}: ${name}`;
  }

  public defaultBloomLevel(roundNumber: number): number {
    switch (roundNumber) {
      case 1: return 1;
      case 2: return 2;
      case 3: return 3;
      case 4: return 4;
      case 5: return 5;
      default: return 1;
    }
  }

  private defaultBloomLevelName(roundNumber: number): string {
    switch (roundNumber) {
      case 1: return 'Remember';
      case 2: return 'Understand';
      case 3: return 'Apply';
      case 4: return 'Analyze';
      case 5: return 'Evaluate';
      default: return 'Remember';
    }
  }

  isRoundSubmitted(): boolean {
    const round = this.currentRoundData();
    if (!round) return false;
    switch (round.type) {
      case 'MCQ': return this.mcqSubmitted();
      case 'MEMORY': return this.memorySubmitted();
      case 'PUZZLE':
      case 'PROBLEM_SOLVING': return this.textSubmitted();
      case 'CODING': return this.codingSubmitted();
      default: return false;
    }
  }
}
