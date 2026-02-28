import { CommonModule } from '@angular/common';
import { Component, inject, OnDestroy, signal } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import {
  CognitiveSprintQuestion,
  CognitiveSprintResultResponse,
  CompilerService
} from '../core/compiler.service';

type SprintPhase = 'idle' | 'round-a' | 'round-b' | 'result';

@Component({
  selector: 'app-cognitive-sprint',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  templateUrl: './cognitive-sprint.component.html',
  styleUrl: './cognitive-sprint.component.css'
})
export class CognitiveSprintComponent implements OnDestroy {
  private compilerService = inject(CompilerService);
  private roundATimer: ReturnType<typeof setInterval> | null = null;
  private roundBStartMs = 0;

  phase = signal<SprintPhase>('idle');
  loading = signal(false);
  error = signal<string | null>(null);

  sprintId = signal('');
  roundATimeLimitSeconds = signal(8);
  roundATimeLeft = signal(8);

  roundAQuestion = signal<CognitiveSprintQuestion | null>(null);
  roundBQuestion = signal<CognitiveSprintQuestion | null>(null);
  roundASelectedIndex = signal<number | null>(null);
  roundBSelectedIndex = signal<number | null>(null);

  roundAFeedback = signal<string | null>(null);
  result = signal<CognitiveSprintResultResponse | null>(null);

  ngOnDestroy(): void {
    this.stopRoundATimer();
  }

  startSprint() {
    this.loading.set(true);
    this.error.set(null);
    this.roundAFeedback.set(null);
    this.result.set(null);

    this.compilerService.startCognitiveSprint().subscribe({
      next: (response) => {
        this.sprintId.set(response.sprintId);
        this.roundATimeLimitSeconds.set(response.system1TimeLimitSeconds || 8);
        this.roundATimeLeft.set(response.system1TimeLimitSeconds || 8);
        this.roundAQuestion.set(response.roundA);
        this.roundBQuestion.set(response.roundB);
        this.roundASelectedIndex.set(null);
        this.roundBSelectedIndex.set(null);
        this.phase.set('round-a');
        this.loading.set(false);
        this.startRoundATimer();
      },
      error: (err) => {
        this.error.set(err?.error?.error || 'Could not start sprint.');
        this.loading.set(false);
      }
    });
  }

  selectRoundA(index: number) {
    if (this.phase() !== 'round-a') return;
    this.roundASelectedIndex.set(index);
  }

  submitRoundA() {
    if (this.phase() !== 'round-a' || this.loading()) return;
    this.stopRoundATimer();
    this.loading.set(true);
    this.error.set(null);

    this.compilerService
      .submitCognitiveSprintRoundA(this.sprintId(), this.roundASelectedIndex() ?? -1)
      .subscribe({
        next: (response) => {
          this.roundAFeedback.set(
            response.correct
              ? `Round A correct in ${Math.round(response.timeTakenMs / 1000)}s.`
              : response.withinTimeLimit
                ? 'Round A incorrect.'
                : 'Round A exceeded 8 seconds.'
          );
          this.phase.set('round-b');
          this.roundBStartMs = Date.now();
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(err?.error?.error || 'Could not submit Round A.');
          this.loading.set(false);
        }
      });
  }

  selectRoundB(index: number) {
    if (this.phase() !== 'round-b') return;
    this.roundBSelectedIndex.set(index);
  }

  submitRoundB() {
    if (this.phase() !== 'round-b' || this.loading()) return;
    this.loading.set(true);
    this.error.set(null);

    this.compilerService
      .submitCognitiveSprintRoundB(this.sprintId(), this.roundBSelectedIndex() ?? -1)
      .subscribe({
        next: (response) => {
          this.result.set(response);
          this.phase.set('result');
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(err?.error?.error || 'Could not submit Round B.');
          this.loading.set(false);
        }
      });
  }

  getThinkingStyleLabel(style: string | undefined): string {
    switch ((style || '').toLowerCase()) {
      case 'system1_dominant':
        return 'System 1 Dominant (fast intuition)';
      case 'system2_deliberate':
        return 'System 2 Deliberate (better with reflection)';
      default:
        return 'Balanced / Needs Practice';
    }
  }

  roundBElapsedSeconds(): number {
    if (!this.roundBStartMs) return 0;
    return Math.max(0, Math.round((Date.now() - this.roundBStartMs) / 1000));
  }

  restart() {
    this.phase.set('idle');
    this.error.set(null);
    this.result.set(null);
    this.roundAFeedback.set(null);
    this.roundAQuestion.set(null);
    this.roundBQuestion.set(null);
    this.roundASelectedIndex.set(null);
    this.roundBSelectedIndex.set(null);
    this.stopRoundATimer();
  }

  private startRoundATimer() {
    this.stopRoundATimer();
    this.roundATimer = setInterval(() => {
      this.roundATimeLeft.update((left) => {
        if (left <= 1) {
          this.stopRoundATimer();
          this.submitRoundA();
          return 0;
        }
        return left - 1;
      });
    }, 1000);
  }

  private stopRoundATimer() {
    if (this.roundATimer) {
      clearInterval(this.roundATimer);
      this.roundATimer = null;
    }
  }
}
