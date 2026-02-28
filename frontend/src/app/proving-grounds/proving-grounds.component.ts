import { CommonModule } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { Router } from '@angular/router';
import { LeetCodeQuestion, QuestionBankService } from '../core/question-bank.service';

@Component({
  selector: 'app-proving-grounds',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule],
  templateUrl: './proving-grounds.component.html',
  styleUrl: './proving-grounds.component.css'
})
export class ProvingGroundsComponent implements OnInit {
  private questionService = inject(QuestionBankService);
  private router = inject(Router);

  // Data
  allQuestions = signal<LeetCodeQuestion[]>([]);
  dailyChallenge = signal<LeetCodeQuestion | null>(null);
  isLoading = signal(true);

  // Filters
  activeCategory = signal<'all' | 'common' | 'top-tier' | 'trending'>('all');
  activeDifficulty = signal<string>('all');
  searchQuery = signal('');

  // Pagination
  currentPage = signal(1);
  pageSize = 20;

  // Computed filtered list
  filteredQuestions = computed(() => {
    let questions = this.allQuestions();
    const diff = this.activeDifficulty();
    const query = this.searchQuery().toLowerCase();

    if (diff !== 'all') {
      questions = questions.filter(q => q.difficulty?.toLowerCase() === diff.toLowerCase());
    }

    if (query) {
      questions = questions.filter(q =>
        q.title.toLowerCase().includes(query) ||
        q.tags?.some(t => t.toLowerCase().includes(query))
      );
    }

    return questions;
  });

  paginatedQuestions = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.filteredQuestions().slice(start, start + this.pageSize);
  });

  totalPages = computed(() => Math.ceil(this.filteredQuestions().length / this.pageSize));

  // Stats
  easyCount = computed(() => this.allQuestions().filter(q => q.difficulty === 'Easy').length);
  mediumCount = computed(() => this.allQuestions().filter(q => q.difficulty === 'Medium').length);
  hardCount = computed(() => this.allQuestions().filter(q => q.difficulty === 'Hard').length);

  ngOnInit() {
    this.loadCategory('all');
    this.questionService.getDailyChallenge().subscribe(q => this.dailyChallenge.set(q));
  }

  loadCategory(category: 'all' | 'common' | 'top-tier' | 'trending') {
    this.activeCategory.set(category);
    this.currentPage.set(1);
    this.isLoading.set(true);

    const obs = category === 'all'
      ? this.questionService.getAllQuestions()
      : category === 'common'
        ? this.questionService.getCommonQuestions()
        : category === 'top-tier'
          ? this.questionService.getTopTierQuestions()
          : this.questionService.getTrendingQuestions();

    obs.subscribe({
      next: (questions) => {
        this.allQuestions.set(questions);
        this.isLoading.set(false);
      },
      error: () => this.isLoading.set(false)
    });
  }

  setDifficulty(diff: string) {
    this.activeDifficulty.set(diff);
    this.currentPage.set(1);
  }

  onSearch(event: Event) {
    const target = event.target as HTMLInputElement;
    this.searchQuery.set(target.value);
    this.currentPage.set(1);
  }

  nextPage() {
    if (this.currentPage() < this.totalPages()) {
      this.currentPage.update(p => p + 1);
    }
  }

  prevPage() {
    if (this.currentPage() > 1) {
      this.currentPage.update(p => p - 1);
    }
  }

  getDifficultyClass(difficulty: string): string {
    switch (difficulty?.toLowerCase()) {
      case 'easy': return 'text-emerald-400 border-emerald-400/30';
      case 'medium': return 'text-amber-400 border-amber-400/30';
      case 'hard': return 'text-crimson-500 border-crimson-500/30';
      default: return 'text-noir-400 border-noir-600';
    }
  }

  openQuestion(url: string) {
    window.open(url, '_blank');
  }

  openQuestionInCompiler(question: LeetCodeQuestion) {
    const slug = (question.slug || this.extractSlug(question.url) || '').trim();
    const resolvedUrl = question.url || (slug ? this.toLeetCodeUrl(slug) : '');
    this.router.navigate(['/compiler'], {
      queryParams: {
        slug,
        title: question.title,
        difficulty: question.difficulty,
        url: resolvedUrl,
        tags: (question.tags || []).join(',')
      }
    });
  }

  private extractSlug(url: string): string {
    const match = (url || '').match(/leetcode\.com\/problems\/([^/?#]+)/i);
    return match?.[1] || '';
  }

  private toLeetCodeUrl(slug: string): string {
    return slug ? `https://leetcode.com/problems/${slug}/` : '';
  }
}
