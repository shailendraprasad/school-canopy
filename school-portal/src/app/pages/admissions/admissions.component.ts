import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-admissions',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './admissions.component.html',
  styleUrl: './admissions.component.css'
})
export class AdmissionsComponent implements OnInit {
  classes = signal<any[]>([]);
  sections = signal<any[]>([]);
  message = signal('');
  error = signal('');
  submitting = signal(false);

  classId = '';
  sectionId = '';
  csvText = '';

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.api.get<any[]>('/api/school/classes').subscribe(res => this.classes.set(res.data || []));
    this.api.get<any[]>('/api/school/sections').subscribe(res => this.sections.set(res.data || []));
  }

  get filteredSections(): any[] {
    if (!this.classId) return [];
    return this.sections().filter(s => s.classId === this.classId);
  }

  onClassChange() { this.sectionId = ''; }

  parseCsv(): any[] {
    const lines = this.csvText.trim().split('\n').filter(l => l.trim());
    if (lines.length < 2) return [];
    const headers = lines[0].split(',').map(h => h.trim().toLowerCase());
    return lines.slice(1).map(line => {
      const values = line.split(',').map(v => v.trim());
      const row: any = {};
      headers.forEach((h, i) => {
        const key = h === 'firstname' ? 'firstName' : h === 'lastname' ? 'lastName' :
                    h === 'parentemail' ? 'parentEmail' : h === 'parentcontact' ? 'parentContact' :
                    h === 'bloodgroup' ? 'bloodGroup' : h;
        row[key] = values[i] || '';
      });
      return row;
    });
  }

  submit() {
    this.message.set('');
    this.error.set('');
    if (!this.sectionId) { this.error.set('Select a target section'); return; }

    const rows = this.parseCsv();
    if (!rows.length) { this.error.set('Paste CSV data with headers: firstName,lastName,parentEmail,relationship'); return; }

    this.submitting.set(true);
    this.api.post<any>('/api/school/admissions/bulk', { sectionId: this.sectionId, rows }).subscribe({
      next: res => {
        const d = res.data;
        this.message.set(`Admitted ${d.admitted} student(s)${d.failed ? `, ${d.failed} failed` : ''}`);
        if (d.errors?.length) {
          this.error.set(d.errors.map((e: any) => `Row ${e.row}: ${e.message}`).join('; '));
        }
        this.csvText = '';
        this.submitting.set(false);
      },
      error: err => {
        this.error.set(err.error?.errors?.[0]?.message || 'Bulk admission failed');
        this.submitting.set(false);
      }
    });
  }

  downloadTemplate() {
    const csv = 'firstName,lastName,parentEmail,relationship,address,parentContact,bloodGroup\nJohn,Doe,parent@email.com,FATHER,,,\n';
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'admissions-template.csv';
    a.click();
    URL.revokeObjectURL(url);
  }
}
