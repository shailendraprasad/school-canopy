import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../services/api.service';
import { environment } from '../../../environments/environment';

interface ParentEntry { relationship: string; email: string; phone: string; }
interface InviteLink { email: string; link: string; }

@Component({
  selector: 'app-student-create',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './student-create.component.html',
  styleUrl: './student-create.component.css'
})
export class StudentCreateComponent implements OnInit {
  classes = signal<any[]>([]);
  sections = signal<any[]>([]);
  firstName = ''; lastName = ''; selectedClassId = ''; selectedSectionId = '';
  bloodGroup = ''; address = '';
  parents: ParentEntry[] = [{ relationship: '', email: '', phone: '' }];
  formError = signal('');
  created = signal(false);
  createdStudentId = '';
  invitationLinks = signal<InviteLink[]>([]);

  constructor(private api: ApiService, private router: Router) {}

  ngOnInit() {
    this.api.get<any[]>('/api/school/classes').subscribe(res => this.classes.set(res.data || []));
    this.api.get<any[]>('/api/school/sections').subscribe(res => this.sections.set(res.data || []));
  }

  getFilteredSections(): any[] { return this.sections().filter(s => s.classId === this.selectedClassId); }
  onClassChange() { this.selectedSectionId = ''; }

  addParent() { this.parents.push({ relationship: '', email: '', phone: '' }); }
  removeParent(i: number) { this.parents.splice(i, 1); }

  getAvailableRelationships(index: number): { value: string; label: string }[] {
    const used = this.parents.filter((_, i) => i !== index).map(p => p.relationship);
    const opts: any[] = [];
    if (!used.includes('MOTHER')) opts.push({ value: 'MOTHER', label: 'Mother' });
    if (!used.includes('FATHER')) opts.push({ value: 'FATHER', label: 'Father' });
    opts.push({ value: 'GUARDIAN', label: 'Guardian' });
    return opts;
  }

  private buildInviteLink(token: string): string {
    return `${environment.parentPortalUrl}/setup/${token}`;
  }

  copyLink(link: string) { navigator.clipboard.writeText(link); }

  submit() {
    this.formError.set('');
    if (!this.firstName.trim()) { this.formError.set('First name is required'); return; }
    if (!this.lastName.trim()) { this.formError.set('Last name is required'); return; }
    if (!this.selectedClassId) { this.formError.set('Class is required'); return; }
    if (!this.selectedSectionId) { this.formError.set('Section is required'); return; }
    const validParents = this.parents.filter(p => p.relationship && p.email.trim());
    if (validParents.length === 0) { this.formError.set('At least one parent/guardian is required'); return; }

    const primary = validParents[0];
    const body: any = {
      firstName: this.firstName.trim(), lastName: this.lastName.trim(),
      sectionId: this.selectedSectionId, relationship: primary.relationship,
      parentEmail: primary.email.trim(), parentContact: primary.phone || ''
    };
    if (this.bloodGroup) body.bloodGroup = this.bloodGroup;
    if (this.address) body.address = this.address;

    this.api.post<any>('/api/school/students', body).subscribe({
      next: (res) => {
        this.createdStudentId = res.data?.id;
        const links: InviteLink[] = [];
        const primaryToken = res.data?.invitationToken;
        if (primaryToken) {
          links.push({ email: primary.email.trim(), link: this.buildInviteLink(primaryToken) });
        }

        const extraParents = validParents.slice(1);
        if (this.createdStudentId && extraParents.length > 0) {
          forkJoin(extraParents.map(p =>
            this.api.post<any>(`/api/school/students/${this.createdStudentId}/parents`, {
              email: p.email.trim(), relationship: p.relationship
            })
          )).subscribe({
            next: (results) => {
              results.forEach((r, i) => {
                const token = r.data?.invitationToken;
                if (token) links.push({ email: extraParents[i].email.trim(), link: this.buildInviteLink(token) });
              });
              this.invitationLinks.set(links);
              this.created.set(true);
            },
            error: () => {
              this.invitationLinks.set(links);
              this.created.set(true);
            }
          });
        } else {
          this.invitationLinks.set(links);
          this.created.set(true);
        }
      },
      error: (err) => this.formError.set(err.error?.errors?.[0]?.message || 'Failed')
    });
  }

  resetForm() {
    this.created.set(false);
    this.invitationLinks.set([]);
    this.createdStudentId = '';
  }
}
