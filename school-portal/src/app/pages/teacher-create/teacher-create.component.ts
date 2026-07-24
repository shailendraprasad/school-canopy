import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-teacher-create',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './teacher-create.component.html',
  styleUrl: './teacher-create.component.css'
})
export class TeacherCreateComponent {
  name = ''; email = ''; phone = ''; gender = '';
  qualification = ''; specialization = ''; employeeId = '';
  dateOfJoining = ''; dateOfBirth = ''; experienceYears: number | null = null;
  aadhaarLast4 = ''; emergencyContact = ''; address = '';

  formError = signal('');
  invitationLink = signal('');
  created = signal(false);

  constructor(private api: ApiService, private router: Router) {}

  submit() {
    this.formError.set('');
    if (!this.name.trim()) { this.formError.set('Name is required'); return; }
    if (!this.email.trim()) { this.formError.set('Email is required'); return; }

    this.api.post<any>('/api/school/teachers/invite', { name: this.name, email: this.email }).subscribe({
      next: (res) => {
        const token = res.data?.invitationToken;
        if (token) {
          this.invitationLink.set(`${window.location.origin}/setup/${token}`);
        }
        // Now update the extended fields
        const teacherId = res.data?.id;
        if (teacherId) {
          const body: any = {};
          if (this.phone) body.phone = this.phone;
          if (this.gender) body.gender = this.gender;
          if (this.qualification) body.qualification = this.qualification;
          if (this.specialization) body.specialization = this.specialization;
          if (this.employeeId) body.employeeId = this.employeeId;
          if (this.dateOfJoining) body.dateOfJoining = this.dateOfJoining;
          if (this.dateOfBirth) body.dateOfBirth = this.dateOfBirth;
          if (this.experienceYears) body.experienceYears = String(this.experienceYears);
          if (this.aadhaarLast4) body.aadhaarLast4 = this.aadhaarLast4;
          if (this.emergencyContact) body.emergencyContact = this.emergencyContact;
          if (this.address) body.address = this.address;
          if (Object.keys(body).length > 0) {
            this.api.patch(`/api/school/teachers/${teacherId}`, body).subscribe();
          }
        }
        this.created.set(true);
      },
      error: (err) => this.formError.set(err.error?.errors?.[0]?.message || 'Failed to create teacher')
    });
  }

  copyLink() { navigator.clipboard.writeText(this.invitationLink()); }
}
