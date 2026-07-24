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
  students = signal<any[]>([]);
  announcements = signal<any[]>([]);
  events = signal<any[]>([]);
  sections = signal<any[]>([]);
  user: any;
  today = new Date();

  // Teacher-specific
  teacherSections = signal<any[]>([]);

  constructor(private api: ApiService, public auth: AuthService) {
    this.user = this.auth.currentUser;
  }

  get isTeacher(): boolean {
    return this.user()?.role === 'TEACHER';
  }

  ngOnInit() {
    this.api.get<any[]>('/api/school/students').subscribe(res => this.students.set(res.data || []));
    this.api.get<any[]>('/api/school/announcements').subscribe({ next: res => this.announcements.set(res.data || []), error: () => {} });
    this.api.get<any[]>('/api/school/events').subscribe({ next: res => this.events.set(res.data || []), error: () => {} });

    // Load sections (teacher sees only their assigned ones)
    this.api.get<any[]>('/api/school/sections').subscribe({
      next: res => {
        const secs = res.data || [];
        this.sections.set(secs);
        // For teacher: load student count per section
        if (this.isTeacher) {
          secs.forEach((sec: any) => {
            this.api.get<any[]>(`/api/school/section-enrollments/${sec.id}/students`).subscribe({
              next: studRes => {
                sec.studentCount = (studRes.data || []).length;
                sec.attendanceMarked = false; // Will check below
                this.teacherSections.set([...secs]);
              },
              error: () => {}
            });
            // Check if attendance is marked today
            const todayStr = new Date().toISOString().split('T')[0];
            this.api.get<any[]>('/api/school/attendance', { sectionId: sec.id, date: todayStr }).subscribe({
              next: attRes => {
                sec.attendanceMarked = (attRes.data || []).length > 0;
                this.teacherSections.set([...secs]);
              },
              error: () => {}
            });
          });
        }
      },
      error: () => {}
    });
  }

  get activeStudents(): number {
    return this.students().filter(s => s.status === 'ACTIVE').length;
  }

  get todayStr(): string {
    return new Date().toISOString().split('T')[0];
  }
}
