import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { SidebarComponent } from '../sidebar/sidebar.component';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, SidebarComponent],
  template: `
    <div class="layout">
      <app-sidebar />
      <main class="content">
        <router-outlet />
      </main>
    </div>
  `,
  styles: [`
    .layout { min-height: 100vh; }
    .content { margin-left: 240px; padding: 28px 36px; background: #f8f8f6; min-height: 100vh; box-sizing: border-box; }
    .content ::ng-deep > * { display: block; width: 100%; }
  `]
})
export class LayoutComponent {}
