import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-teacher-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './teacher-detail.component.html',
  styleUrl: './teacher-detail.component.css'
})
export class TeacherDetailComponent implements OnInit {
  teacherId = '';
  teacher = signal<any>(null);
  sections = signal<any[]>([]);
  editing = signal(false);
  saveMsg = signal('');

  // Editable fields
  name = ''; phone = ''; gender = ''; qualification = ''; specialization = '';
  employeeId = ''; dateOfJoining = ''; dateOfBirth = ''; experienceYears: number | null = null;
  aadhaarLast4 = ''; emergencyContact = ''; address = '';

  constructor(private api: ApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit() {
    this.teacherId = this.route.snapshot.paramMap.get('id') || '';
    if (this.teacherId === 'new') return; // handled by create page
    this.loadTeacher();
    this.loadSections();
  }

  loadTeacher() {
    this.api.get<any[]>('/api/school/teachers').subscribe(res => {
      const t = (res.data || []).find((x: any) => x.id === this.teacherId);
      if (t) { this.teacher.set(t); this.populateForm(t); }
    });
  }

  loadSections() {
    // Get all sections, then for each check if this teacher is assigned
    this.api.get<any[]>('/api/school/sections').subscribe(res => {
      const allSections = res.data || [];
      allSections.forEach((sec: any) => {
        this.api.get<any[]>(`/api/school/section-enrollments/${sec.id}/teachers`).subscribe(tRes => {
          const teachers = tRes.data || [];
          sec.isAssigned = teachers.some((t: any) => t.id === this.teacherId);
          this.sections.set([...allSections]);
        });
      });
      this.sections.set(allSections);
    });
  }

  populateForm(t: any) {
    this.name = t.name || ''; this.phone = t.phone || ''; this.gender = t.gender || '';
    this.qualification = t.qualification || ''; this.specialization = t.specialization || '';
    this.employeeId = t.employeeId || ''; this.dateOfJoining = t.dateOfJoining || '';
    this.dateOfBirth = t.dateOfBirth || ''; this.experienceYears = t.experienceYears;
    this.aadhaarLast4 = t.aadhaarLast4 || ''; this.emergencyContact = t.emergencyContact || '';
    this.address = t.address || '';
  }

  startEdit() { this.editing.set(true); this.saveMsg.set(''); }
  cancelEdit() { this.editing.set(false); this.populateForm(this.teacher()); }

  save() {
    this.saveMsg.set('');
    const body: any = {
      name: this.name, phone: this.phone, gender: this.gender,
      qualification: this.qualification, specialization: this.specialization,
      employeeId: this.employeeId, dateOfJoining: this.dateOfJoining || null,
      dateOfBirth: this.dateOfBirth || null, aadhaarLast4: this.aadhaarLast4,
      emergencyContact: this.emergencyContact, address: this.address
    };
    if (this.experienceYears !== null) body.experienceYears = String(this.experienceYears);

    this.api.patch(`/api/school/teachers/${this.teacherId}`, body).subscribe({
      next: () => { this.editing.set(false); this.saveMsg.set('Saved successfully!'); this.loadTeacher(); },
      error: () => this.saveMsg.set('Failed to save')
    });
  }

  toggleStatus(status: string) {
    this.api.patch(`/api/school/teachers/${this.teacherId}/status`, { status }).subscribe(() => this.loadTeacher());
  }

  get assignedSections(): any[] { return this.sections().filter(s => s.isAssigned); }
}
