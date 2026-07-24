import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-class-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './class-detail.component.html',
  styleUrl: './class-detail.component.css'
})
export class ClassDetailComponent implements OnInit {
  classId = '';
  classInfo = signal<any>(null);
  sections = signal<any[]>([]);
  teachers = signal<any[]>([]);
  allTeachers = signal<any[]>([]);
  user: any;

  // Assign teacher modal
  showAssignTeacher = signal(false);
  assignSectionId = '';
  assignTeacherId = '';
  assignError = signal('');
  assignSuccess = signal('');

  constructor(private api: ApiService, private auth: AuthService, private route: ActivatedRoute) {
    this.user = this.auth.currentUser;
  }

  ngOnInit() {
    this.classId = this.route.snapshot.paramMap.get('id') || '';
    this.load();
  }

  load() {
    // Load class info
    this.api.get<any[]>('/api/school/classes').subscribe(res => {
      const classes = res.data || [];
      const cls = classes.find((c: any) => c.id === this.classId);
      if (cls) this.classInfo.set(cls);
    });

    // Load sections for this class
    this.api.get<any[]>('/api/school/sections').subscribe(res => {
      const allSections = res.data || [];
      const classSections = allSections.filter((s: any) => s.classId === this.classId);
      this.sections.set(classSections);

      // For each section, load teachers and students
      classSections.forEach((section: any) => {
        this.loadSectionDetails(section);
      });
    });

    // Load all teachers for assignment dropdown
    this.api.get<any[]>('/api/school/teachers').subscribe(res => {
      this.allTeachers.set(res.data || []);
    });
  }

  loadSectionDetails(section: any) {
    // Load teachers for section
    this.api.get<any[]>(`/api/school/section-enrollments/${section.id}/teachers`).subscribe(res => {
      section.teachers = res.data || [];
      this.sections.update(s => [...s]);
    });

    // Load students for section
    this.api.get<any[]>(`/api/school/section-enrollments/${section.id}/students`).subscribe(res => {
      section.students = res.data || [];
      this.sections.update(s => [...s]);
    });
  }

  openAssignTeacher(sectionId: string) {
    this.assignSectionId = sectionId;
    this.assignTeacherId = '';
    this.assignError.set('');
    this.assignSuccess.set('');
    this.showAssignTeacher.set(true);
  }

  assignTeacher() {
    if (!this.assignTeacherId) {
      this.assignError.set('Please select a teacher');
      return;
    }
    this.assignError.set('');
    this.api.post(`/api/school/section-enrollments/${this.assignSectionId}/teachers`, {
      teacherId: this.assignTeacherId
    }).subscribe({
      next: () => {
        this.assignSuccess.set('Teacher assigned successfully!');
        this.showAssignTeacher.set(false);
        this.load();
      },
      error: (err) => {
        this.assignError.set(err.error?.errors?.[0]?.message || 'Failed to assign teacher');
      }
    });
  }

  unassignTeacher(sectionId: string, teacherId: string) {
    this.api.delete(`/api/school/section-enrollments/${sectionId}/teachers/${teacherId}`).subscribe({
      next: () => this.load(),
      error: () => {}
    });
  }

  getAvailableTeachers(sectionId: string): any[] {
    const section = this.sections().find(s => s.id === sectionId);
    const assignedIds = (section?.teachers || []).map((t: any) => t.id);
    return this.allTeachers().filter(t => !assignedIds.includes(t.id));
  }

  get isAdmin(): boolean {
    const role = this.user()?.role;
    return role === 'SCHOOL_ADMINISTRATOR' || role === 'OFFICE_STAFF';
  }

  getTotalStudents(): number {
    return this.sections().reduce((sum, s) => sum + (s.students?.length || 0), 0);
  }

  getTotalTeachers(): number {
    const teacherIds = new Set<string>();
    this.sections().forEach(s => (s.teachers || []).forEach((t: any) => teacherIds.add(t.id)));
    return teacherIds.size;
  }
}
