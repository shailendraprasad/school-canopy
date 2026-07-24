import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-staff-create',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './staff-create.component.html',
  styleUrl: './staff-create.component.css'
})
export class StaffCreateComponent {
  name = ''; email = ''; phone = ''; designation = ''; gender = '';
  employeeId = ''; dateOfJoining = ''; emergencyContact = '';
  formError = signal('');
  invitationLink = signal('');
  created = signal(false);

  constructor(private api: ApiService, private router: Router) {}

  submit() {
    this.formError.set('');
    if (!this.name.trim()) { this.formError.set('Name is required'); return; }
    if (!this.email.trim()) { this.formError.set('Email is required'); return; }

    this.api.post<any>('/api/school/office-staff', {
      name: this.name, email: this.email, phone: this.phone, designation: this.designation
    }).subscribe({
      next: (res) => {
        const token = res.data?.invitationToken;
        if (token) this.invitationLink.set(`${window.location.origin}/setup/${token}`);
        this.created.set(true);
      },
      error: (err) => this.formError.set(err.error?.errors?.[0]?.message || 'Failed')
    });
  }

  copyLink() { navigator.clipboard.writeText(this.invitationLink()); }
}
