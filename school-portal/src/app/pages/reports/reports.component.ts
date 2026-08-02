import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { ApiService } from '../../services/api.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './reports.component.html',
  styleUrl: './reports.component.css'
})
export class ReportsComponent implements OnInit {
  years = signal<any[]>([]);
  classes = signal<any[]>([]);
  sections = signal<any[]>([]);
  attendanceReport = signal<any[]>([]);
  enrollmentSnapshot = signal<any[]>([]);
  loading = signal(false);
  activeTab = signal<'attendance' | 'enrollment'>('attendance');

  academicYearId = '';
  classId = '';
  sectionId = '';
  fromDate = '';
  toDate = '';

  constructor(private api: ApiService, private http: HttpClient) {}

  ngOnInit() {
    const today = new Date();
    this.toDate = today.toISOString().split('T')[0];
    const from = new Date(today);
    from.setDate(from.getDate() - 90);
    this.fromDate = from.toISOString().split('T')[0];

    this.api.get<any[]>('/api/school/academic-years').subscribe(res => {
      const years = res.data || [];
      this.years.set(years);
      const active = years.find((y: any) => y.status === 'ACTIVE');
      this.academicYearId = active?.id ?? years[0]?.id ?? '';
      this.loadReport();
    });
    this.api.get<any[]>('/api/school/classes').subscribe(res => this.classes.set(res.data || []));
    this.api.get<any[]>('/api/school/sections').subscribe(res => this.sections.set(res.data || []));
  }

  get filteredSections(): any[] {
    if (!this.classId) return this.sections();
    return this.sections().filter(s => s.classId === this.classId);
  }

  onClassChange() { this.sectionId = ''; }

  loadReport() {
    this.loading.set(true);
    const params: any = { academicYearId: this.academicYearId, from: this.fromDate, to: this.toDate };
    if (this.classId) params.classId = this.classId;
    if (this.sectionId) params.sectionId = this.sectionId;

    if (this.activeTab() === 'attendance') {
      this.api.get<any>('/api/school/reports/attendance', params).subscribe({
        next: res => { this.attendanceReport.set(res.data?.rows || []); this.loading.set(false); },
        error: () => this.loading.set(false)
      });
    } else {
      const snapParams: any = { academicYearId: this.academicYearId };
      if (this.classId) snapParams.classId = this.classId;
      this.api.get<any>('/api/school/reports/enrollment', snapParams).subscribe({
        next: res => { this.enrollmentSnapshot.set(res.data?.rows || []); this.loading.set(false); },
        error: () => this.loading.set(false)
      });
    }
  }

  switchTab(tab: 'attendance' | 'enrollment') {
    this.activeTab.set(tab);
    this.loadReport();
  }

  exportCsv() {
    const params: any = { academicYearId: this.academicYearId, from: this.fromDate, to: this.toDate };
    if (this.classId) params.classId = this.classId;
    if (this.sectionId) params.sectionId = this.sectionId;

    const token = localStorage.getItem('school_token');
    let headers = new HttpHeaders();
    if (token) headers = headers.set('Authorization', `Bearer ${token}`);

    const query = new URLSearchParams(params).toString();
    this.http.get(`${environment.apiUrl}/api/school/reports/attendance/export?${query}`, {
      headers, responseType: 'blob'
    }).subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'attendance-report.csv';
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  get avgAttendance(): number {
    const rows = this.attendanceReport();
    if (!rows.length) return 0;
    return Math.round(rows.reduce((sum: number, r: any) => sum + (r.percentage || 0), 0) / rows.length);
  }

  get totalEnrolled(): number {
    return this.enrollmentSnapshot().reduce((sum: number, r: any) => sum + (r.studentCount || 0), 0);
  }
}
