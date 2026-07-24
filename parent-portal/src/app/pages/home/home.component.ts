import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css'
})
export class HomeComponent implements OnInit {
  children = signal<any[]>([]);
  announcements = signal<any[]>([]);
  events = signal<any[]>([]);
  unreadMessages = signal(0);

  constructor(public auth: AuthService, private api: ApiService) {}

  ngOnInit() {
    this.api.get<any[]>('/api/parent/children').subscribe(res => {
      const kids = res.data || [];
      this.children.set(kids);
      // Load attendance and teacher for each child
      kids.forEach((child: any) => {
        this.api.get<any>(`/api/parent/children/${child.id}/attendance`).subscribe({
          next: attRes => {
            child.attendance = attRes.data;
            this.children.set([...this.children()]);
          },
          error: () => {}
        });
      });
    });
    this.api.get<any[]>('/api/parent/announcements').subscribe(res => this.announcements.set((res.data || []).slice(0, 5)));
    this.api.get<any[]>('/api/parent/events').subscribe(res => this.events.set((res.data || []).slice(0, 5)));
    this.api.get<any[]>('/api/parent/messages').subscribe(res => this.unreadMessages.set((res.data || []).length));
    // Load teachers to map to children
    this.api.get<any[]>('/api/parent/teachers').subscribe(res => {
      const teachers = res.data || [];
      // Map teacher to child
      const kids = this.children();
      kids.forEach((child: any) => {
        const teacherForChild = teachers.find((t: any) => t.studentId?.toString() === child.id?.toString());
        if (teacherForChild) {
          child.teacherName = teacherForChild.teacherName;
          child.teacherEmail = teacherForChild.teacherEmail;
        }
      });
      this.children.set([...kids]);
    });
  }

  getAttendanceColor(pct: number): string {
    if (pct >= 90) return '#2d6a4f';
    if (pct >= 75) return '#ba7517';
    return '#b0473f';
  }

  getAttendanceBg(pct: number): string {
    if (pct >= 90) return '#e7f3ea';
    if (pct >= 75) return '#fdf1e2';
    return '#fbeaea';
  }

  getNextEvent(): any {
    return this.events().length > 0 ? this.events()[0] : null;
  }

  getDaysUntil(dateStr: string): number {
    if (!dateStr) return 0;
    const target = new Date(dateStr + 'T00:00:00');
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return Math.ceil((target.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));
  }
}
