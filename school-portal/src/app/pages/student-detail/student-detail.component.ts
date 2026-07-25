import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-student-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './student-detail.component.html',
  styleUrl: './student-detail.component.css'
})
export class StudentDetailComponent implements OnInit {
  student = signal<any>(null);
  parents = signal<any[]>([]);
  attendance = signal<any>(null);
  studentId = '';
  parentEmail = '';
  parentRelationship = '';
  linkMessage = signal('');
  invitationLink = signal('');

  constructor(private api: ApiService, public auth: AuthService, private route: ActivatedRoute) {}

  ngOnInit() {
    this.studentId = this.route.snapshot.paramMap.get('id') || '';
    this.loadStudent();
    this.loadParents();
    this.loadAttendance();
  }

  loadStudent() {
    this.api.get<any>(`/api/school/students/${this.studentId}`).subscribe({
      next: res => { if (res.data) this.student.set(res.data); },
      error: () => {}
    });
  }

  loadParents() {
    this.api.get<any[]>(`/api/school/students/${this.studentId}/parents`).subscribe(res => this.parents.set(res.data || []));
  }

  loadAttendance() {
    // Get attendance for the student's section
    this.api.get<any>(`/api/school/students/${this.studentId}`).subscribe(res => {
      const s = res.data;
      if (s?.sectionId) {
        this.api.get<any[]>('/api/school/attendance/summary', { sectionId: s.sectionId }).subscribe({
          next: attRes => {
            const data = attRes.data || [];
            const studentAtt = data.find((r: any) => r.studentId?.toString() === this.studentId || r.studentCode === s.studentId);
            if (studentAtt) {
              const total = studentAtt.totalDays || 0;
              this.attendance.set({
                totalDays: total,
                presentDays: studentAtt.presentDays || 0,
                absentDays: studentAtt.absentDays || 0,
                lateDays: studentAtt.lateDays || 0,
                percentage: total > 0 ? Math.round(((studentAtt.presentDays || 0) + (studentAtt.lateDays || 0)) * 100 / total) : 0
              });
            }
          },
          error: () => {}
        });
      }
    });
  }

  linkParent() {
    this.linkMessage.set('');
    this.invitationLink.set('');
    const body: any = { email: this.parentEmail };
    if (this.parentRelationship) body.relationship = this.parentRelationship;
    this.api.post<any>(`/api/school/students/${this.studentId}/parents`, body).subscribe({
      next: (res) => {
        const email = this.parentEmail;
        this.parentEmail = '';
        this.parentRelationship = '';
        const token = res.data?.invitationToken;
        if (token) {
          this.invitationLink.set(`${environment.parentPortalUrl}/setup/${token}`);
          this.linkMessage.set(`Parent linked. Share the invitation link with ${email}.`);
        } else {
          this.linkMessage.set('Parent linked successfully!');
        }
        this.loadParents();
      },
      error: (err) => this.linkMessage.set(err.error?.errors?.[0]?.message || 'Failed to link parent')
    });
  }

  copyInviteLink() {
    navigator.clipboard.writeText(this.invitationLink());
  }

  get attendanceColor(): string {
    const pct = this.attendance()?.percentage || 0;
    if (pct >= 90) return '#2d6a4f';
    if (pct >= 75) return '#ba7517';
    return '#b0473f';
  }

  get attendanceBg(): string {
    const pct = this.attendance()?.percentage || 0;
    if (pct >= 90) return '#e7f3ea';
    if (pct >= 75) return '#fdf1e2';
    return '#fbeaea';
  }
}
