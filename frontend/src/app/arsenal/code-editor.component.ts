import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';

interface ExecutionResult {
  success: boolean;
  output?: string;
  error?: string;
  executionTime?: string;
  language?: string;
  timestamp?: string;
}

@Component({
  selector: 'app-code-editor',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule],
  template: `
    <div class="min-h-screen bg-noir-950 p-6 animate-fade-in">
      <div class="max-w-6xl mx-auto">
        <!-- Header -->
        <div class="mb-8">
          <h1 class="text-4xl font-black uppercase tracking-tighter text-white mb-2">Code Arsenal</h1>
          <p class="text-noir-400 font-mono text-sm">Write, compile, and execute code in multiple languages.</p>
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
          
          <!-- Editor Panel -->
          <div class="space-y-4">
            <!-- Language Selector -->
            <div>
              <label class="block text-xs font-bold uppercase tracking-widest text-noir-500 mb-2">Language</label>
              <select 
                [(ngModel)]="selectedLanguage"
                (change)="onLanguageChange()"
                class="w-full bg-noir-900 border border-noir-800 rounded-lg px-4 py-2 text-noir-100 font-mono focus:outline-none focus:border-crimson-500 transition-colors"
              >
                <option value="java">Java</option>
                <option value="python">Python</option>
                <option value="cpp">C++</option>
                <option value="javascript">JavaScript</option>
              </select>
            </div>

            <!-- Code Editor -->
            <div>
              <label class="block text-xs font-bold uppercase tracking-widest text-noir-500 mb-2">Source Code</label>
              <textarea 
                [(ngModel)]="sourceCode"
                [placeholder]="getPlaceholder()"
                class="w-full h-80 bg-noir-900 border border-noir-800 rounded-lg px-4 py-3 text-noir-100 font-mono text-sm focus:outline-none focus:border-crimson-500 transition-colors resize-none"
              ></textarea>
              <div class="text-[10px] text-noir-600 mt-2 font-mono">
                Lines: {{ sourceCode.split('\n').length }} | Characters: {{ sourceCode.length }}
              </div>
            </div>

            <!-- Input -->
            <div>
              <label class="block text-xs font-bold uppercase tracking-widest text-noir-500 mb-2">Input (Optional)</label>
              <textarea 
                [(ngModel)]="input"
                placeholder="Enter input data here (one per line)"
                class="w-full h-24 bg-noir-900 border border-noir-800 rounded-lg px-4 py-3 text-noir-100 font-mono text-sm focus:outline-none focus:border-crimson-500 transition-colors resize-none"
              ></textarea>
            </div>

            <!-- Execution Controls -->
            <div class="flex gap-3">
              <button 
                (click)="executeCode()"
                [disabled]="isLoading() || !sourceCode.trim()"
                class="flex-1 bg-emerald-600 hover:bg-emerald-500 disabled:bg-noir-700 text-white font-bold py-2 rounded-lg transition-colors uppercase tracking-widest text-xs flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <lucide-icon name="Play" class="w-4 h-4" *ngIf="!isLoading()"></lucide-icon>
                <lucide-icon name="Loader2" class="w-4 h-4 animate-spin" *ngIf="isLoading()"></lucide-icon>
                <span>{{ isLoading() ? 'EXECUTING...' : 'EXECUTE' }}</span>
              </button>
              <button 
                (click)="resetCode()"
                [disabled]="isLoading()"
                class="px-4 bg-noir-800 hover:bg-noir-700 disabled:opacity-50 text-white font-bold py-2 rounded-lg transition-colors uppercase tracking-widest text-xs flex items-center gap-2 disabled:cursor-not-allowed"
              >
                <lucide-icon name="RotateCcw" class="w-4 h-4"></lucide-icon>
                RESET
              </button>
              <button 
                (click)="clearOutput()"
                [disabled]="!executionResult()"
                class="px-4 bg-noir-800 hover:bg-noir-700 disabled:opacity-50 text-white font-bold py-2 rounded-lg transition-colors uppercase tracking-widest text-xs flex items-center gap-2 disabled:cursor-not-allowed"
              >
                <lucide-icon name="Trash2" class="w-4 h-4"></lucide-icon>
                CLEAR
              </button>
            </div>
          </div>

          <!-- Output Panel -->
          <div class="space-y-4">
            <!-- Available Languages Info -->
            <div class="bg-noir-900 border border-noir-800 rounded-lg p-4">
              <h3 class="text-xs font-bold uppercase tracking-widest text-noir-500 mb-3">Available Languages</h3>
              <div class="space-y-2">
                @for (compiler of availableCompilers(); track compiler.languageName) {
                  <div class="flex justify-between items-center text-xs font-mono">
                    <span class="text-noir-300">{{ compiler.languageName }}</span>
                    <span class="text-noir-600">{{ compiler.version }}</span>
                  </div>
                }
                @if (availableCompilers().length === 0) {
                  <p class="text-xs text-noir-600 font-mono">Checking available compilers...</p>
                }
              </div>
            </div>

            <!-- Execution Result -->
            @if (executionResult()) {
              <div class="space-y-3">
                <!-- Status Header -->
                <div class="flex items-center gap-2 p-3 rounded-lg"
                  [class.bg-emerald-950/30]="executionResult()!.success"
                  [class.bg-crimson-950/30]="!executionResult()!.success"
                >
                  <lucide-icon 
                    [name]="executionResult()!.success ? 'CheckCircle' : 'AlertCircle'"
                    class="w-5 h-5"
                    [class.text-emerald-500]="executionResult()!.success"
                    [class.text-crimson-500]="!executionResult()!.success"
                  ></lucide-icon>
                  <div>
                    <div class="text-xs font-bold uppercase tracking-widest"
                      [class.text-emerald-500]="executionResult()!.success"
                      [class.text-crimson-500]="!executionResult()!.success"
                    >
                      {{ executionResult()!.success ? 'EXECUTION SUCCESSFUL' : 'EXECUTION FAILED' }}
                    </div>
                    @if (executionResult()!.executionTime) {
                      <div class="text-[10px] text-noir-500 font-mono mt-1">{{ executionResult()!.executionTime }}</div>
                    }
                  </div>
                </div>

                <!-- Output Area -->
                <div>
                  <label class="block text-xs font-bold uppercase tracking-widest text-noir-500 mb-2">Output</label>
                  <div class="bg-black border border-noir-800 rounded-lg p-4 min-h-32 max-h-96 overflow-y-auto font-mono text-xs text-noir-200 whitespace-pre-wrap break-words">
                    {{ executionResult()!.output || '(No output)' }}
                  </div>
                </div>

                <!-- Error Area (if any) -->
                @if (!executionResult()!.success && executionResult()!.error) {
                  <div>
                    <label class="block text-xs font-bold uppercase tracking-widest text-crimson-500 mb-2">Error</label>
                    <div class="bg-crimson-950/20 border border-crimson-500/30 rounded-lg p-4 min-h-24 max-h-48 overflow-y-auto font-mono text-xs text-crimson-400 whitespace-pre-wrap break-words">
                      {{ executionResult()!.error }}
                    </div>
                  </div>
                }
              </div>
            }

            <!-- Default Message -->
            @if (!executionResult()) {
              <div class="flex items-center justify-center h-full min-h-96 bg-noir-900/50 border border-dashed border-noir-800 rounded-lg">
                <div class="text-center">
                  <lucide-icon name="Code2" class="w-12 h-12 text-noir-700 mx-auto mb-4"></lucide-icon>
                  <p class="text-noir-400 font-mono text-sm uppercase tracking-widest">Write code and execute</p>
                </div>
              </div>
            }
          </div>
        </div>

        <!-- Code Samples -->
        <div class="mt-8">
          <h2 class="text-2xl font-black uppercase tracking-tighter text-white mb-4">Code Templates</h2>
          <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            @for (sample of codeSamples(); track sample.language) {
              <button 
                (click)="loadSample(sample)"
                class="noir-card p-4 hover:border-crimson-500 transition-colors text-left group"
              >
                <div class="text-xs font-bold uppercase tracking-widest text-noir-500 group-hover:text-crimson-500 transition-colors">{{ sample.language }}</div>
                <div class="text-sm font-mono text-noir-300 mt-2 line-clamp-3">{{ sample.code.substring(0, 50) }}...</div>
              </button>
            }
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
    }
  `]
})
export class CodeEditorComponent {
  private http = inject(HttpClient);

  selectedLanguage = 'java';
  sourceCode = '';
  input = '';
  isLoading = signal(false);
  executionResult = signal<ExecutionResult | null>(null);
  availableCompilers = signal<any[]>([]);

  codeSamples = signal([
    {
      language: 'Java',
      code: `public class Solution {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}`
    },
    {
      language: 'Python',
      code: `def main():
    print("Hello, World!")

if __name__ == "__main__":
    main()`
    },
    {
      language: 'C++',
      code: `#include <iostream>
using namespace std;

int main() {
    cout << "Hello, World!" << endl;
    return 0;
}`
    },
    {
      language: 'JavaScript',
      code: `function main() {
    console.log("Hello, World!");
}

main();`
    }
  ]);

  constructor() {
    this.fetchAvailableCompilers();
  }

  fetchAvailableCompilers() {
    this.http.get<any[]>('/api/code-execution/compilers').subscribe({
      next: (compilers) => {
        this.availableCompilers.set(compilers);
      },
      error: (err) => {
        console.error('Failed to fetch available compilers', err);
      }
    });
  }

  executeCode() {
    if (!this.sourceCode.trim()) {
      return;
    }

    this.isLoading.set(true);
    this.executionResult.set(null);

    const payload = {
      language: this.selectedLanguage,
      sourceCode: this.sourceCode,
      input: this.input,
      timeoutSeconds: 10
    };

    this.http.post<ExecutionResult>('/api/code-execution/execute', payload).subscribe({
      next: (result) => {
        this.executionResult.set(result);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.executionResult.set({
          success: false,
          error: err.error?.error || 'Execution failed. Please check your code.'
        });
        this.isLoading.set(false);
      }
    });
  }

  onLanguageChange() {
    // Auto-select appropriate template
    const sample = this.codeSamples().find(s => 
      s.language.toLowerCase() === this.selectedLanguage.toLowerCase()
    );
    // Clear on language change but don't auto-load to avoid UX confusion
  }

  loadSample(sample: any) {
    this.sourceCode = sample.code;
    // Auto-select the language
    const langMap: { [key: string]: string } = {
      'Java': 'java',
      'Python': 'python',
      'C++': 'cpp',
      'JavaScript': 'javascript'
    };
    this.selectedLanguage = langMap[sample.language] || 'java';
  }

  resetCode() {
    this.sourceCode = '';
    this.input = '';
  }

  clearOutput() {
    this.executionResult.set(null);
  }

  getPlaceholder(): string {
    const placeholders: { [key: string]: string } = {
      'java': `public class Solution {
    public static void main(String[] args) {
        // Your code here
    }
}`,
      'python': `# Your Python code here
def main():
    pass

if __name__ == "__main__":
    main()`,
      'cpp': `#include <iostream>
using namespace std;

int main() {
    // Your code here
    return 0;
}`,
      'javascript': `// Your JavaScript code here
function main() {
    // Your code here
}

main();`
    };
    return placeholders[this.selectedLanguage] || 'Enter your code...';
  }
}
