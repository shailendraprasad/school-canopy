import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-academic-years',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './academic-years.component.html',
  styleUrl: './academic-years.component.css'
})
export class AcademicYearsComponent implements OnInit {
  years = signal<any[]>([]);
  showForm = signal(false);
  message = signal('');
  error = signal('');

  name = '';
  startsOn = '';
  endsOn = '';

  constructor(private api: ApiService) {}

  ngOnInit() { this.load(); }

  load() {
    this.api.get<any[]>('/api/school/academic-years').subscribe({
      next: res => this.years.set(res.data || []),
      error: () => this.error.set('Failed to load academic years')
    });
  }

  get activeCount(): number { return this.years().filter(y => y.status === 'ACTIVE').length; }
  get plannedCount(): number { return this.years().filter(y => y.status === 'PLANNED').length; }
  get closedCount(): number { return this.years().filter(y => y.status === 'CLOSED').length; }

  openForm() {
    this.name = '';
    this.startsOn = '';
    this.endsOn = '';
    this.error.set('');
    this.showForm.set(true);
  }

  create() {
    this.error.set('');
    this.api.post('/api/school/academic-years', { name: this.name, startsOn: this.startsOn, endsOn: this.endsOn }).subscribe({
      next: () => {
        this.showForm.set(false);
        this.message.set('Academic year created successfully');
        this.load();
      },
      error: err => this.error.set(err.error?.errors?.[0]?.message || 'Failed to create academic year')
    });
  }

  activate(id: string) {
    this.message.set('');
    this.error.set('');
    this.api.post(`/api/school/academic-years/${id}/activate`, {}).subscribe({
      next: () => { this.message.set('Academic year activated'); this.load(); },
      error: err => this.error.set(err.error?.errors?.[0]?.message || 'Failed to activate')
    });
  }

  close(id: string) {
    this.message.set('');
    this.error.set('');
    this.api.post(`/api/school/academic-years/${id}/close`, {}).subscribe({
      next: () => { this.message.set('Academic year closed'); this.load(); },
      error: err => this.error.set(err.error?.errors?.[0]?.message || 'Failed to close')
    });
  }

  dismissMessage() { this.message.set(''); }
}
