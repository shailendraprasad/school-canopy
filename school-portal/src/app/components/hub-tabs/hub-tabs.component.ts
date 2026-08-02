import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';

export interface HubTab {
  path: string;
  label: string;
  icon: string;
}

@Component({
  selector: 'app-hub-tabs',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  template: `
    <nav class="hub-tabs" aria-label="Section navigation">
      @for (tab of tabs; track tab.path) {
        <a [routerLink]="tab.path" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }" class="hub-tab">
          <span class="hub-tab-icon">{{ tab.icon }}</span>
          <span class="hub-tab-label">{{ tab.label }}</span>
        </a>
      }
    </nav>
  `,
  styles: [`
    .hub-tabs {
      display: flex;
      gap: 6px;
      flex-wrap: wrap;
      margin-bottom: 20px;
      padding: 6px;
      background: #fff;
      border-radius: 12px;
      box-shadow: 0 1px 3px rgba(0,0,0,.04);
      border: 1px solid #ececec;
    }
    .hub-tab {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 10px 16px;
      border-radius: 8px;
      text-decoration: none;
      color: #666;
      font-size: .88rem;
      font-weight: 500;
      transition: all .15s;
      white-space: nowrap;
    }
    .hub-tab:hover { background: #f4f6f8; color: #1a1a2e; }
    .hub-tab.active {
      background: #4a6b8a;
      color: #fff;
      font-weight: 600;
      box-shadow: 0 2px 6px rgba(74, 107, 138, .25);
    }
    .hub-tab-icon { font-size: 1rem; line-height: 1; }
    @media (max-width: 640px) {
      .hub-tab { flex: 1 1 auto; justify-content: center; padding: 10px 12px; }
      .hub-tab-label { font-size: .82rem; }
    }
  `]
})
export class HubTabsComponent {
  @Input() tabs: HubTab[] = [];
}
