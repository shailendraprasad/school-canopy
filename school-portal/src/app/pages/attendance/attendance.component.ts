import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

interface AttendanceRecord {
  studentId: string;
  studentName: string;
  studentCode: string;
  status: 'PRESENT' | 'ABSENT' | 'LATE';
}

interface SummaryRecord {
  studentId: string;
  studentName: string;
  studentCode: string;
  presentPercent: number;
  absentPercent: number;
  latePercent: number;
  totalDays: number;
  presentDays: number;
  absentDays: number;
  lateDays: number;
}

@Component({
  selector: 'app-attendance',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './attendance.component.html',
  styleUrl: './attendance.component.css'
})
export class AttendanceComponent implements OnInit {
  classes = signal<any[]>([]);
  sections = signal<any[]>([]);
  records = signal<AttendanceRecord[]>([]);
  summary = signal<SummaryRecord[]>([]);

  selectedClassId = '';
  selectedSectionId = '';
  selectedDate = '';
  activeTab: 'mark' | 'summary' = 'mark';

  successMessage = signal('');
  errorMessage = signal('');
  loading = signal(false);
  attendanceAlreadyMarked = signal(false);

  constructor(private api: ApiService, public auth: AuthService) {}

  ngOnInit() {
    this.selectedDate = this.todayString();
    this.loadClasses();
    this.loadSections();
  }

  private todayString(): string {
    const d = new Date();
    return d.toISOString().split('T')[0];
  }

  loadClasses() {
    this.api.get<any[]>('/api/school/classes').subscribe(res => {
      this.classes.set(res.data || []);
    });
  }

  loadSections() {
    this.api.get<any[]>('/api/school/sections').subscribe(res => {
      this.sections.set(res.data || []);
    });
  }

  getFilteredSections(): any[] {
    if (!this.selectedClassId) return [];
    return this.sections().filter(s => s.classId === this.selectedClassId);
  }

  getSelectedClassName(): string {
    const cls = this.classes().find(c => c.id === this.selectedClassId);
    return cls?.name || '';
  }

  getSelectedSectionName(): string {
    const sec = this.sections().find(s => s.id === this.selectedSectionId);
    return sec?.name || '';
  }

  onClassChange() {
    this.selectedSectionId = '';
    this.records.set([]);
    this.summary.set([]);
    this.successMessage.set('');
    this.errorMessage.set('');
    this.attendanceAlreadyMarked.set(false);
  }

  onSelectionChange() {
    this.successMessage.set('');
    this.errorMessage.set('');
    if (this.selectedSectionId) {
      if (this.activeTab === 'mark' && this.selectedDate) {
        this.loadStudentsAndAttendance();
      } else if (this.activeTab === 'summary') {
        this.loadSummary();
      }
    }
  }

  onDateChange() {
    this.successMessage.set('');
    this.errorMessage.set('');
    if (this.selectedSectionId && this.selectedDate) {
      this.loadStudentsAndAttendance();
    }
  }

  loadStudentsAndAttendance() {
    this.loading.set(true);
    this.attendanceAlreadyMarked.set(false);
    this.api.get<any[]>(`/api/school/section-enrollments/${this.selectedSectionId}/students`).subscribe({
      next: (res) => {
        const studentList = res.data || [];

        this.api.get<any[]>('/api/school/attendance', {
          sectionId: this.selectedSectionId,
          date: this.selectedDate
        }).subscribe({
          next: (attRes) => {
            const existing = attRes.data || [];
            if (existing.length > 0) {
              this.attendanceAlreadyMarked.set(true);
            }
            const recs: AttendanceRecord[] = studentList.map((s: any) => {
              const found = existing.find((a: any) => a.studentId === s.id);
              return {
                studentId: s.id,
                studentName: s.name,
                studentCode: s.studentId || '',
                status: found?.status || 'PRESENT'
              };
            });
            this.records.set(recs);
            this.loading.set(false);
          },
          error: () => {
            const recs: AttendanceRecord[] = studentList.map((s: any) => ({
              studentId: s.id,
              studentName: s.name,
              studentCode: s.studentId || '',
              status: 'PRESENT' as const
            }));
            this.records.set(recs);
            this.loading.set(false);
          }
        });
      },
      error: () => {
        this.records.set([]);
        this.loading.set(false);
      }
    });
  }

  markAll(status: 'PRESENT' | 'ABSENT' | 'LATE') {
    const updated = this.records().map(r => ({ ...r, status }));
    this.records.set(updated);
  }

  get presentCount(): number { return this.records().filter(r => r.status === 'PRESENT').length; }
  get absentCount(): number { return this.records().filter(r => r.status === 'ABSENT').length; }
  get lateCount(): number { return this.records().filter(r => r.status === 'LATE').length; }

  saveAttendance() {
    this.successMessage.set('');
    this.errorMessage.set('');
    const body = {
      sectionId: this.selectedSectionId,
      date: this.selectedDate,
      records: this.records().map(r => ({ studentId: r.studentId, status: r.status }))
    };
    this.api.post('/api/school/attendance', body).subscribe({
      next: () => {
        this.successMessage.set('Attendance saved successfully for ' + this.records().length + ' students!');
        this.attendanceAlreadyMarked.set(true);
      },
      error: (err) => this.errorMessage.set(err.error?.errors?.[0]?.message || 'Failed to save attendance.')
    });
  }

  loadSummary() {
    if (!this.selectedSectionId) return;
    this.loading.set(true);
    this.api.get<any[]>('/api/school/attendance/summary', {
      sectionId: this.selectedSectionId
    }).subscribe({
      next: (res) => {
        const data = res.data || [];
        const mapped: SummaryRecord[] = data.map((r: any) => {
          const total = r.totalDays || 0;
          return {
            studentId: r.studentId,
            studentName: r.studentName,
            studentCode: r.studentCode || '',
            presentDays: r.presentDays || 0,
            absentDays: r.absentDays || 0,
            lateDays: r.lateDays || 0,
            presentPercent: total > 0 ? Math.round((r.presentDays || 0) * 100 / total) : 0,
            absentPercent: total > 0 ? Math.round((r.absentDays || 0) * 100 / total) : 0,
            latePercent: total > 0 ? Math.round((r.lateDays || 0) * 100 / total) : 0,
            totalDays: total
          };
        });
        this.summary.set(mapped);
        this.loading.set(false);
      },
      error: () => {
        this.summary.set([]);
        this.loading.set(false);
      }
    });
  }

  get overallAttendanceRate(): number {
    const data = this.summary();
    if (data.length === 0) return 0;
    const totalPresent = data.reduce((sum, r) => sum + r.presentDays + r.lateDays, 0);
    const totalDays = data.reduce((sum, r) => sum + r.totalDays, 0);
    return totalDays > 0 ? Math.round(totalPresent * 100 / totalDays) : 0;
  }

  switchTab(tab: 'mark' | 'summary') {
    this.activeTab = tab;
    this.successMessage.set('');
    this.errorMessage.set('');
    if (tab === 'summary' && this.selectedSectionId) {
      this.loadSummary();
    } else if (tab === 'mark' && this.selectedSectionId && this.selectedDate) {
      this.loadStudentsAndAttendance();
    }
  }

  getFormattedDate(): string {
    if (!this.selectedDate) return '';
    const d = new Date(this.selectedDate + 'T00:00:00');
    return d.toLocaleDateString('en-US', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
  }
}
