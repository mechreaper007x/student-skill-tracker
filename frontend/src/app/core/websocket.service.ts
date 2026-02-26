import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export interface DuelMessage {
  username?: string;
  codeDelta?: string;
  status?: string;
  passedCases?: number;
  totalCases?: number;
}

export interface RoundData {
  round: number;
  type: 'MCQ' | 'MEMORY' | 'PUZZLE' | 'PROBLEM_SOLVING' | 'CODING';
  title: string;
  timeLimitSeconds: number;
  // MCQ
  questions?: Array<{
    question: string;
    options: string[];
    correctIndex: number;
  }>;
  // MEMORY
  gridSize?: number;
  pairs?: Array<{ id: number; content: string; match: string }>;
  // PUZZLE
  puzzleHtml?: string;
  answer?: string;
  hints?: string[];
  // PROBLEM_SOLVING
  problemHtml?: string;
  solutionExplanation?: string;
  // CODING
  descriptionHtml?: string;
  starterCode?: { java: string; python: string; cpp: string; javascript: string };
  hiddenTestCases?: Array<{ input: string; expectedOutput: string }>;
}

export interface MatchStartPayload {
  sessionId: string;
  player1: string;
  player2: string;
  puzzle: {
    title: string;
    descriptionHtml: string;
    difficulty: string;
    roundsJson: string;
    starterCodeJava: string;
    starterCodePython: string;
    starterCodeCpp: string;
    starterCodeJavascript: string;
    hiddenTestCasesJson: string;
  };
}

export interface AnswerResult {
  username: string;
  round: number;
  pointsAwarded: number;
  isCorrect: boolean;
  player1Score: number;
  player2Score: number;
}

export interface RoundAdvance {
  status: 'NEXT_ROUND' | 'FINISHED';
  currentRound?: number;
  winner?: string;
  player1Score: number;
  player2Score: number;
}

@Injectable({
  providedIn: 'root'
})
export class WebSocketService {
  private http = inject(HttpClient);
  private platformId = inject(PLATFORM_ID);

  public connected = signal(false);
  public matchStart = signal<MatchStartPayload | null>(null);
  public opponentTyping = signal<DuelMessage | null>(null);
  public opponentStatus = signal<DuelMessage | null>(null);
  public answerResult = signal<AnswerResult | null>(null);
  public roundAdvance = signal<RoundAdvance | null>(null);

  private client!: Client;
  private matchSub?: StompSubscription;
  private typingSub?: StompSubscription;
  private statusSub?: StompSubscription;
  private answerSub?: StompSubscription;
  private roundSub?: StompSubscription;
  private isClientActivated = false;
  private pendingConnectionActions: Array<() => void> = [];

  constructor() {
    if (!isPlatformBrowser(this.platformId)) return;

    this.client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      debug: (msg: string) => console.log('[STOMP]', msg),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        this.connected.set(true);
        const pending = [...this.pendingConnectionActions];
        this.pendingConnectionActions = [];
        for (const action of pending) {
          action();
        }
      },
      onDisconnect: () => {
        this.connected.set(false);
        this.matchStart.set(null);
      },
      onWebSocketError: (event) => {
        console.error('[STOMP][WS-ERROR]', event);
      },
      onStompError: (frame) => {
        console.error('[STOMP][BROKER-ERROR]', frame.headers['message'], frame.body);
      }
    });
  }

  public subscribeToMatchmaking(username: string): Promise<void> {
    return new Promise((resolve) => {
      this.runWhenConnected(() => {
        if (this.matchSub) this.matchSub.unsubscribe();

        this.matchSub = this.client.subscribe(`/topic/${username}/duel-start`, (message: IMessage) => {
          const payload: MatchStartPayload = JSON.parse(message.body);
          this.matchStart.set(payload);
          this.subscribeToSession(payload.sessionId);
        });

        resolve();
      });
    });
  }

  private subscribeToSession(sessionId: string) {
    this.runWhenConnected(() => {
      if (this.typingSub) this.typingSub.unsubscribe();
      if (this.statusSub) this.statusSub.unsubscribe();
      if (this.answerSub) this.answerSub.unsubscribe();
      if (this.roundSub) this.roundSub.unsubscribe();

      this.typingSub = this.client.subscribe(`/topic/duel/${sessionId}/typing`, (message: IMessage) => {
        this.opponentTyping.set(JSON.parse(message.body));
      });

      this.statusSub = this.client.subscribe(`/topic/duel/${sessionId}/executeStatus`, (message: IMessage) => {
        this.opponentStatus.set(JSON.parse(message.body));
      });

      this.answerSub = this.client.subscribe(`/topic/duel/${sessionId}/answerResult`, (message: IMessage) => {
        this.answerResult.set(JSON.parse(message.body));
      });

      this.roundSub = this.client.subscribe(`/topic/duel/${sessionId}/roundAdvance`, (message: IMessage) => {
        this.roundAdvance.set(JSON.parse(message.body));
      });
    });
  }

  public sendTypingDelta(sessionId: string, username: string, codeDelta: string) {
    if (!this.connected()) return;
    this.client.publish({
      destination: `/app/duel/${sessionId}/typing`,
      body: JSON.stringify({ username, codeDelta })
    });
  }

  public sendExecuteStatus(sessionId: string, username: string, status: string, passedCases?: number, totalCases?: number) {
    if (!this.connected()) return;
    this.client.publish({
      destination: `/app/duel/${sessionId}/executeStatus`,
      body: JSON.stringify({ username, status, passedCases, totalCases })
    });
  }

  public submitAnswer(sessionId: string, username: string, answer: string) {
    if (!this.connected()) return;
    this.client.publish({
      destination: `/app/duel/${sessionId}/submitAnswer`,
      body: JSON.stringify({ username, answer })
    });
  }

  public advanceRound(sessionId: string, username: string) {
    if (!this.connected()) return;
    this.client.publish({
      destination: `/app/duel/${sessionId}/advanceRound`,
      body: JSON.stringify({ username })
    });
  }

  public joinLobby() {
    return this.http.post('/api/duel/lobby/join', {});
  }

  public leaveLobby() {
    return this.http.post('/api/duel/lobby/leave', {});
  }

  private runWhenConnected(action: () => void) {
    if (!isPlatformBrowser(this.platformId)) return;

    if (this.connected()) {
      action();
      return;
    }

    this.pendingConnectionActions.push(action);

    if (!this.isClientActivated) {
      this.client.activate();
      this.isClientActivated = true;
    }
  }
}
