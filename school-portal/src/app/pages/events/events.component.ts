import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-events',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './events.component.html',
  styleUrl: './events.component.css'
})
export class EventsComponent implements OnInit {
  events = signal<any[]>([]);
  classes = signal<any[]>([]);
  sections = signal<any[]>([]);
  showForm = signal(false);
  user: any;

  // Calendar state
  currentYear = new Date().getFullYear();
  currentMonth = new Date().getMonth();
  selectedDate = signal<string>('');

  // Form
  title = ''; eventDate = ''; startTime = ''; endTime = ''; location = '';
  scopeType = 'SCHOOL'; scopeId = ''; selectedClassId = '';
  formError = signal('');

  constructor(private api: ApiService, public auth: AuthService) { this.user = this.auth.currentUser; }

  ngOnInit() { this.load(); this.loadStructure(); }

  load() { this.api.get<any[]>('/api/school/events').subscribe(res => this.events.set(res.data || [])); }

  loadStructure() {
    this.api.get<any[]>('/api/school/classes').subscribe(res => this.classes.set(res.data || []));
    this.api.get<any[]>('/api/school/sections').subscribe(res => this.sections.set(res.data || []));
  }

  // === Calendar Logic ===

  get monthName(): string {
    return new Date(this.currentYear, this.currentMonth).toLocaleString('default', { month: 'long', year: 'numeric' });
  }

  get calendarDays(): { date: number; inMonth: boolean; dateStr: string; isToday: boolean; hasEvent: boolean }[] {
    const firstDay = new Date(this.currentYear, this.currentMonth, 1).getDay();
    const daysInMonth = new Date(this.currentYear, this.currentMonth + 1, 0).getDate();
    const daysInPrevMonth = new Date(this.currentYear, this.currentMonth, 0).getDate();
    const today = new Date().toISOString().split('T')[0];

    const eventDates = new Set(this.events().map(e => e.eventDate));
    const days: any[] = [];

    // Previous month trailing days
    for (let i = firstDay - 1; i >= 0; i--) {
      const d = daysInPrevMonth - i;
      const m = this.currentMonth === 0 ? 12 : this.currentMonth;
      const y = this.currentMonth === 0 ? this.currentYear - 1 : this.currentYear;
      const dateStr = `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      days.push({ date: d, inMonth: false, dateStr, isToday: false, hasEvent: eventDates.has(dateStr) });
    }

    // Current month days
    for (let d = 1; d <= daysInMonth; d++) {
      const dateStr = `${this.currentYear}-${String(this.currentMonth + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      days.push({ date: d, inMonth: true, dateStr, isToday: dateStr === today, hasEvent: eventDates.has(dateStr) });
    }

    // Next month leading days
    const remaining = 42 - days.length;
    for (let d = 1; d <= remaining; d++) {
      const m = this.currentMonth === 11 ? 1 : this.currentMonth + 2;
      const y = this.currentMonth === 11 ? this.currentYear + 1 : this.currentYear;
      const dateStr = `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      days.push({ date: d, inMonth: false, dateStr, isToday: false, hasEvent: eventDates.has(dateStr) });
    }

    return days;
  }

  prevMonth() {
    if (this.currentMonth === 0) { this.currentMonth = 11; this.currentYear--; }
    else { this.currentMonth--; }
  }

  nextMonth() {
    if (this.currentMonth === 11) { this.currentMonth = 0; this.currentYear++; }
    else { this.currentMonth++; }
  }

  selectDay(day: any) {
    if (day.inMonth) {
      this.selectedDate.set(day.dateStr);
    }
  }

  get selectedDateEvents(): any[] {
    if (!this.selectedDate()) return [];
    return this.events().filter(e => e.eventDate === this.selectedDate());
  }

  get upcomingEvents(): any[] {
    const today = new Date().toISOString().split('T')[0];
    return this.events().filter(e => e.eventDate >= today).slice(0, 8);
  }

  formatEventDate(dateStr: string): string {
    if (!dateStr) return '';
    const d = new Date(dateStr + 'T00:00:00');
    return d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
  }

  formatSelectedDate(): string {
    if (!this.selectedDate()) return '';
    const d = new Date(this.selectedDate() + 'T00:00:00');
    return d.toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' });
  }

  // === Form Logic ===

  getFilteredSections(): any[] {
    if (!this.selectedClassId) return [];
    return this.sections().filter(s => s.classId === this.selectedClassId);
  }

  onScopeTypeChange() { this.scopeId = ''; this.selectedClassId = ''; }
  onClassChange() { this.scopeId = ''; }

  openCreateForm() {
    this.eventDate = this.selectedDate() || new Date().toISOString().split('T')[0];
    this.showForm.set(true);
  }

  onCreate() {
    this.formError.set('');
    const body: any = { title: this.title, eventDate: this.eventDate, scopeType: this.scopeType };
    if (this.scopeType === 'CLASS' && this.selectedClassId) body.scopeId = this.selectedClassId;
    else if (this.scopeType === 'SECTION' && this.scopeId) body.scopeId = this.scopeId;
    if (this.startTime) body.startTime = this.startTime;
    if (this.endTime) body.endTime = this.endTime;
    if (this.location) body.location = this.location;

    this.api.post('/api/school/events', body).subscribe({
      next: () => {
        this.showForm.set(false);
        this.title = ''; this.eventDate = ''; this.startTime = ''; this.endTime = '';
        this.location = ''; this.scopeType = 'SCHOOL'; this.scopeId = ''; this.selectedClassId = '';
        this.load();
      },
      error: (err) => this.formError.set(err.error?.errors?.[0]?.message || 'Failed to create event')
    });
  }
}
