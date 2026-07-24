import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit {
  stats = signal<any>(null);
  schoolHealth = signal<any[]>([]);
  config = signal<any>(null);

  constructor(private api: ApiService, public auth: AuthService) {}

  ngOnInit() {
    this.api.get<any>('/api/platform/dashboard').subscribe(res => this.stats.set(res.data));
    this.api.get<any[]>('/api/platform/dashboard/school-health').subscribe(res => this.schoolHealth.set(res.data || []));
    this.api.get<any>('/api/platform/config').subscribe(res => this.config.set(res.data));
  }

  getAttendanceColor(rate: number): string {
    if (rate >= 85) return '#2d6a4f';
    if (rate >= 70) return '#ba7517';
    return '#b0473f';
  }

  getAttendanceBg(rate: number): string {
    if (rate >= 85) return '#e7f3ea';
    if (rate >= 70) return '#fdf1e2';
    return '#fbeaea';
  }

  getRelativeTime(dateStr: string): string {
    if (!dateStr) return 'Never';
    const date = new Date(dateStr);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return diffMins + 'm ago';
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return diffHours + 'h ago';
    const diffDays = Math.floor(diffHours / 24);
    if (diffDays < 7) return diffDays + 'd ago';
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  }
}
