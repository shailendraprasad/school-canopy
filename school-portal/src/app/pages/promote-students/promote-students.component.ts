import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';

type PromotionAction = 'PROMOTE' | 'RETAIN' | 'GRADUATE' | 'LEAVE';

interface StudentAction {
  studentId: string;
  name: string;
  studentCode: string;
  action: PromotionAction;
}

@Component({
  selector: 'app-promote-students',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './promote-students.component.html',
  styleUrl: './promote-students.component.css'
})
export class PromoteStudentsComponent implements OnInit {
  years = signal<any[]>([]);
  classes = signal<any[]>([]);
  sections = signal<any[]>([]);
  previewStudents = signal<StudentAction[]>([]);

  step = signal(1);
  message = signal('');
  error = signal('');
  loading = signal(false);
  submitting = signal(false);
  copyingTeachers = signal(false);

  fromYearId = '';
  fromClassId = '';
  fromSectionId = '';
  toYearId = '';
  toClassId = '';
  toSectionId = '';

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.api.get<any[]>('/api/school/academic-years').subscribe(res => this.years.set(res.data || []));
    this.api.get<any[]>('/api/school/classes').subscribe(res => this.classes.set(res.data || []));
    this.api.get<any[]>('/api/school/sections').subscribe(res => this.sections.set(res.data || []));
  }

  get fromYears(): any[] {
    return this.years().filter(y => y.status === 'ACTIVE' || y.status === 'CLOSED');
  }

  get toYears(): any[] {
    return this.years().filter(y => y.status === 'PLANNED' || y.status === 'ACTIVE');
  }

  getSectionsForClass(classId: string): any[] {
    return this.sections().filter(s => s.classId === classId);
  }

  onFromClassChange() { this.fromSectionId = ''; }
  onToClassChange() { this.toSectionId = ''; }

  canProceedStep1(): boolean {
    return !!(this.fromYearId && this.fromClassId && this.fromSectionId);
  }

  canProceedStep2(): boolean {
    return !!(this.toYearId && this.toClassId && this.toSectionId);
  }

  goToStep2() {
    if (!this.canProceedStep1()) return;
    this.step.set(2);
    this.error.set('');
  }

  loadPreview() {
    if (!this.canProceedStep2()) return;
    this.loading.set(true);
    this.error.set('');
    this.api.get<any[]>('/api/school/promotions/preview', {
      fromYearId: this.fromYearId,
      sectionId: this.fromSectionId
    }).subscribe({
      next: res => {
        const students = (res.data || []).map((s: any) => ({
          studentId: s.id,
          name: s.name,
          studentCode: s.studentId,
          action: 'PROMOTE' as PromotionAction
        }));
        this.previewStudents.set(students);
        this.step.set(3);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err.error?.errors?.[0]?.message || 'Failed to load students');
        this.loading.set(false);
      }
    });
  }

  back() {
    const current = this.step();
    if (current > 1) this.step.set(current - 1);
    this.error.set('');
  }

  copyTeacherAssignments() {
    if (!this.fromYearId || !this.toYearId) {
      this.error.set('Select both source and target academic years first');
      return;
    }
    this.copyingTeachers.set(true);
    this.error.set('');
    this.api.post('/api/school/section-enrollments/copy-teacher-assignments', {
      fromYearId: this.fromYearId,
      toYearId: this.toYearId
    }).subscribe({
      next: (res: any) => {
        const copied = res.data?.copied ?? 'Teacher assignments';
        this.message.set(`${copied} teacher assignment(s) copied successfully`);
        this.copyingTeachers.set(false);
      },
      error: err => {
        this.error.set(err.error?.errors?.[0]?.message || 'Failed to copy teacher assignments');
        this.copyingTeachers.set(false);
      }
    });
  }

  submit() {
    const students = this.previewStudents();
    if (!students.length) return;

    this.submitting.set(true);
    this.error.set('');

    const actions = students.map(s => {
      const action: any = { studentId: s.studentId, action: s.action };
      if (s.action === 'PROMOTE') action.toSectionId = this.toSectionId;
      return action;
    });

    this.api.post('/api/school/promotions', {
      fromYearId: this.fromYearId,
      toYearId: this.toYearId,
      actions
    }).subscribe({
      next: (res: any) => {
        const r = res.data || {};
        this.message.set(`Promotion complete: ${r.promoted || 0} promoted, ${r.retained || 0} retained, ${r.graduated || 0} graduated, ${r.left || 0} left`);
        this.submitting.set(false);
        this.step.set(1);
        this.previewStudents.set([]);
        this.fromYearId = '';
        this.fromClassId = '';
        this.fromSectionId = '';
        this.toYearId = '';
        this.toClassId = '';
        this.toSectionId = '';
      },
      error: err => {
        this.error.set(err.error?.errors?.[0]?.message || 'Promotion failed');
        this.submitting.set(false);
      }
    });
  }

  dismissMessage() { this.message.set(''); }

  getYearName(id: string): string {
    return this.years().find(y => y.id === id)?.name || '';
  }

  getClassName(id: string): string {
    return this.classes().find(c => c.id === id)?.name || '';
  }

  getSectionName(id: string): string {
    return this.sections().find(s => s.id === id)?.name || '';
  }
}
