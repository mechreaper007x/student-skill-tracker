import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-skill-radar',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="noir-card p-5 border-l-2 border-l-noir-500">
      <h3 class="text-sm font-black uppercase mb-4 text-noir-200">Skill Radar (Score Table)</h3>
      <div class="space-y-2">
        <div *ngFor="let item of entries" class="grid grid-cols-[160px_1fr_56px] gap-3 items-center">
          <span class="text-xs text-noir-400 uppercase">{{ item.key }}</span>
          <div class="h-2 bg-noir-800">
            <div class="h-2 bg-crimson-600" [style.width.%]="item.value"></div>
          </div>
          <span class="text-xs text-noir-200 text-right">{{ item.value | number: '1.0-1' }}</span>
        </div>
      </div>
    </section>
  `
})
export class SkillRadarComponent {
  @Input() radar: Record<string, number> | null = null;

  get entries(): Array<{ key: string; value: number }> {
    if (!this.radar) {
      return [];
    }
    return Object.entries(this.radar).map(([key, value]) => ({ key, value }));
  }
}
