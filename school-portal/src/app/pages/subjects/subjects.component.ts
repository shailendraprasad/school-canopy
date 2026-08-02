import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-subjects',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './subjects.component.html',
  styleUrl: './subjects.component.css'
})
export class SubjectsComponent implements OnInit {
  subjects = signal<any[]>([]);
  assignments = signal<any[]>([]);
  teachers = signal<any[]>([]);
  classes = signal<any[]>([]);
  sections = signal<any[]>([]);
  message = signal('');
  error = signal('');
  showForm = signal(false);
  showAssignForm = signal(false);

  name = '';
  code = '';
  assignSubjectId = '';
  assignTeacherId = '';
  assignClassId = '';
  assignSectionId = '';

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.load();
    this.api.get<any[]>('/api/school/teachers').subscribe(res => this.teachers.set(res.data || []));
    this.api.get<any[]>('/api/school/classes').subscribe(res => this.classes.set(res.data || []));
    this.api.get<any[]>('/api/school/sections').subscribe(res => this.sections.set(res.data || []));
  }

  load() {
    this.api.get<any[]>('/api/school/subjects').subscribe(res => this.subjects.set(res.data || []));
    this.api.get<any[]>('/api/school/subjects/assignments').subscribe(res => this.assignments.set(res.data || []));
  }

  get assignSections(): any[] {
    if (!this.assignClassId) return [];
    return this.sections().filter(s => s.classId === this.assignClassId);
  }

  create() {
    this.error.set('');
    this.api.post('/api/school/subjects', { name: this.name, code: this.code }).subscribe({
      next: () => {
        this.name = ''; this.code = '';
        this.showForm.set(false);
        this.message.set('Subject created');
        this.load();
      },
      error: err => this.error.set(err.error?.errors?.[0]?.message || 'Failed to create subject')
    });
  }

  assign() {
    this.error.set('');
    this.api.post(`/api/school/subjects/${this.assignSubjectId}/assign`, {
      teacherId: this.assignTeacherId,
      sectionId: this.assignSectionId
    }).subscribe({
      next: () => {
        this.showAssignForm.set(false);
        this.assignSubjectId = ''; this.assignTeacherId = ''; this.assignClassId = ''; this.assignSectionId = '';
        this.message.set('Teacher assigned to subject');
        this.load();
      },
      error: err => this.error.set(err.error?.errors?.[0]?.message || 'Failed to assign')
    });
  }

  unassign(id: string) {
    this.api.delete(`/api/school/subjects/assignments/${id}`).subscribe({
      next: () => { this.message.set('Assignment removed'); this.load(); },
      error: () => {}
    });
  }
}
