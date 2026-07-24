import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-announcements',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="announcements-page">
      <h1>📢 Announcements</h1>
      <p class="subtitle">Important updates from your children's school</p>

      <div class="announcement-list">
        @for (ann of announcements(); track ann.id) {
          <div class="announcement-card">
            <div class="card-header">
              <span class="badge" [attr.data-category]="ann.category">{{ ann.category }}</span>
              <span class="scope">{{ ann.scopeType }}</span>
            </div>
            <h3>{{ ann.title }}</h3>
            <p class="body">{{ ann.body }}</p>
            <p class="meta">{{ ann.createdAt | date:'mediumDate' }}</p>
          </div>
        } @empty {
          <div class="empty-state">
            <p>📭 No announcements to display</p>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .announcements-page { }
    h1 { font-size: 22px; margin-bottom: 4px; }
    .subtitle { color: #666; font-size: 14px; margin-bottom: 24px; }
    .announcement-list { display: flex; flex-direction: column; gap: 16px; }
    .announcement-card {
      background: #fff;
      border-radius: 12px;
      padding: 20px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.06);
    }
    .card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
    .badge {
      padding: 3px 10px;
      border-radius: 4px;
      font-size: 11px;
      font-weight: 600;
      background: #e8f0fe;
      color: #4a6b8a;
      text-transform: uppercase;
    }
    .scope { font-size: 11px; color: #999; text-transform: uppercase; }
    .announcement-card h3 { font-size: 16px; margin-bottom: 8px; }
    .body { font-size: 14px; color: #444; line-height: 1.6; margin-bottom: 12px; }
    .meta { font-size: 12px; color: #999; }
    .empty-state { text-align: center; padding: 48px; color: #999; }
  `]
})
export class AnnouncementsComponent implements OnInit {
  announcements = signal<any[]>([]);

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.api.get<any[]>('/api/parent/announcements').subscribe(res => this.announcements.set(res.data || []));
  }
}
