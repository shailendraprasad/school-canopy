import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-schools',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './schools.component.html',
  styleUrl: './schools.component.css'
})
export class SchoolsComponent implements OnInit {
  schools = signal<any[]>([]);
  showForm = signal(false);
  expandedSchoolId = signal<string>('');

  // Form fields
  name = '';
  prefix = '';
  contactEmail = '';
  address = '';
  phone = '';
  boardAffiliation = '';
  udiseCode = '';
  schoolType = '';
  mediumOfInstruction = '';
  foundedYear: number | null = null;
  city = '';
  state = '';
  pinCode = '';
  principalName = '';
  principalPhone = '';
  website = '';
  formError = signal('');
  formLoading = signal(false);

  // Filter
  statusFilter = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  ngOnInit() { this.loadSchools(); }

  loadSchools() {
    this.api.get<any[]>('/api/platform/dashboard/school-health').subscribe(res => this.schools.set(res.data || []));
  }

  get filteredSchools(): any[] {
    if (!this.statusFilter) return this.schools();
    return this.schools().filter(s => s.status === this.statusFilter);
  }

  toggleExpand(schoolId: string) {
    this.expandedSchoolId.set(this.expandedSchoolId() === schoolId ? '' : schoolId);
  }

  onSubmit() {
    this.formError.set('');
    this.formLoading.set(true);
    this.api.post('/api/platform/schools', {
      name: this.name, prefix: this.prefix.toUpperCase(),
      contactEmail: this.contactEmail, address: this.address, phone: this.phone,
      boardAffiliation: this.boardAffiliation, udiseCode: this.udiseCode,
      schoolType: this.schoolType, mediumOfInstruction: this.mediumOfInstruction,
      foundedYear: this.foundedYear, city: this.city, state: this.state,
      pinCode: this.pinCode, principalName: this.principalName,
      principalPhone: this.principalPhone, website: this.website
    }).subscribe({
      next: () => { this.formLoading.set(false); this.showForm.set(false); this.resetForm(); this.loadSchools(); },
      error: (err) => { this.formLoading.set(false); this.formError.set(err.error?.errors?.[0]?.message || 'Failed'); }
    });
  }

  toggleStatus(school: any, event: Event) {
    event.stopPropagation();
    const id = school.id?.toString ? school.id.toString() : school.id;
    const newStatus = school.status === 'ACTIVE' ? 'DEACTIVATED' : 'ACTIVE';
    this.api.patch(`/api/platform/schools/${id}/status`, { status: newStatus }).subscribe({
      next: () => this.loadSchools(),
      error: () => {}
    });
  }

  openSchoolPortal(school: any, event: Event) {
    event.stopPropagation();
    window.open('http://localhost:3001', '_blank');
  }

  resetForm() {
    this.name = ''; this.prefix = ''; this.contactEmail = ''; this.address = ''; this.phone = '';
    this.boardAffiliation = ''; this.udiseCode = ''; this.schoolType = ''; this.mediumOfInstruction = '';
    this.foundedYear = null; this.city = ''; this.state = ''; this.pinCode = '';
    this.principalName = ''; this.principalPhone = ''; this.website = '';
  }

  getAttendanceColor(rate: number): string {
    if (rate >= 85) return '#2d6a4f';
    if (rate >= 70) return '#ba7517';
    return '#b0473f';
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
    return diffDays + 'd ago';
  }

  get totalStudents(): number { return this.schools().reduce((sum, s) => sum + (s.studentCount || 0), 0); }
  get totalTeachers(): number { return this.schools().reduce((sum, s) => sum + (s.teacherCount || 0), 0); }
  get activeSchools(): number { return this.schools().filter(s => s.status === 'ACTIVE').length; }
}
