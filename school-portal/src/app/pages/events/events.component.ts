import { Component, OnInit, signal } from '@angular/core';

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

  allEvents: any[] = [];

  events = signal<any[]>([]);

  academicYears = signal<any[]>([]);

  selectedYearId = '';

  classes = signal<any[]>([]);

  sections = signal<any[]>([]);

  showForm = signal(false);

  editingId = signal<string | null>(null);

  selectedEvent = signal<any | null>(null);

  deleteConfirmId = signal<string | null>(null);

  user: any;



  currentYear = new Date().getFullYear();

  currentMonth = new Date().getMonth();

  selectedDate = signal<string>('');



  filterScope = '';

  filterClassId = '';

  filterScopeId = '';



  title = ''; eventDate = ''; startTime = ''; endTime = ''; location = '';

  scopeType = 'SCHOOL'; scopeId = ''; selectedClassId = '';

  formError = signal('');



  constructor(private api: ApiService, public auth: AuthService) { this.user = this.auth.currentUser; }



  ngOnInit() {

    this.api.get<any[]>('/api/school/academic-years').subscribe(res => {

      const years = res.data || [];

      this.academicYears.set(years);

      const active = years.find((y: any) => y.status === 'ACTIVE');

      this.selectedYearId = active?.id ?? years[0]?.id ?? '';

      this.load();

    });

    this.loadStructure();

  }



  load() {

    const params = this.selectedYearId ? { academicYearId: this.selectedYearId } : undefined;

    this.api.get<any[]>('/api/school/events', params).subscribe(res => {

      this.allEvents = res.data || [];

      this.applyFilter();

    });

  }



  applyFilter() {

    let list = this.allEvents;

    if (this.filterScope) list = list.filter(e => e.scopeType === this.filterScope);

    if (this.filterScope === 'CLASS' && this.filterClassId) {

      list = list.filter(e => e.scopeId === this.filterClassId);

    }

    if (this.filterScope === 'SECTION' && this.filterClassId && !this.filterScopeId) {

      const classSectionIds = this.sections().filter(s => s.classId === this.filterClassId).map(s => s.id);

      list = list.filter(e => classSectionIds.includes(e.scopeId));

    }

    if (this.filterScope === 'SECTION' && this.filterScopeId) {

      list = list.filter(e => e.scopeId === this.filterScopeId);

    }

    this.events.set(list);

  }



  onYearChange() { this.load(); }



  onFilterScopeChange() {

    this.filterClassId = '';

    this.filterScopeId = '';

    this.applyFilter();

  }



  onFilterClassChange() {

    this.filterScopeId = '';

    this.applyFilter();

  }



  loadStructure() {

    this.api.get<any[]>('/api/school/classes').subscribe(res => this.classes.set(res.data || []));

    this.api.get<any[]>('/api/school/sections').subscribe(res => this.sections.set(res.data || []));

  }



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



    for (let i = firstDay - 1; i >= 0; i--) {

      const d = daysInPrevMonth - i;

      const m = this.currentMonth === 0 ? 12 : this.currentMonth;

      const y = this.currentMonth === 0 ? this.currentYear - 1 : this.currentYear;

      const dateStr = `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;

      days.push({ date: d, inMonth: false, dateStr, isToday: false, hasEvent: eventDates.has(dateStr) });

    }



    for (let d = 1; d <= daysInMonth; d++) {

      const dateStr = `${this.currentYear}-${String(this.currentMonth + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;

      days.push({ date: d, inMonth: true, dateStr, isToday: dateStr === today, hasEvent: eventDates.has(dateStr) });

    }



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

    if (day.inMonth) this.selectedDate.set(day.dateStr);

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



  getFilteredSections(): any[] {

    if (!this.selectedClassId) return [];

    return this.sections().filter(s => s.classId === this.selectedClassId);

  }



  getFilteredSectionsFor(classId: string): any[] {

    if (!classId) return [];

    return this.sections().filter(s => s.classId === classId);

  }



  getAudiencePreview(): string {

    if (this.scopeType === 'SCHOOL') return 'All parents in the school will see this event.';

    if (this.scopeType === 'CLASS') {

      const cls = this.classes().find(c => c.id === this.selectedClassId);

      return cls ? `Parents of students in ${cls.name} will see this.` : 'Select a class to target parents.';

    }

    if (this.scopeType === 'SECTION') {

      const sec = this.sections().find(s => s.id === this.scopeId);

      const cls = this.classes().find(c => c.id === this.selectedClassId);

      return sec && cls ? `Parents of students in ${cls.name} · Section ${sec.name} will see this.` : 'Select class and section to target parents.';

    }

    return '';

  }



  getScopeLabel(e: any): string {

    return e.scopeLabel || e.scopeType;

  }



  onScopeTypeChange() { this.scopeId = ''; this.selectedClassId = ''; }

  onClassChange() { this.scopeId = ''; }



  openCreateForm() {

    this.resetForm();

    this.editingId.set(null);

    this.eventDate = this.selectedDate() || new Date().toISOString().split('T')[0];

    this.showForm.set(true);

  }



  openEditForm(e: any) {

    this.editingId.set(e.id);

    this.title = e.title;

    this.eventDate = e.eventDate;

    this.startTime = e.startTime || '';

    this.endTime = e.endTime || '';

    this.location = e.location || '';

    this.scopeType = e.scopeType || 'SCHOOL';

    this.selectedClassId = e.scopeClassId || (e.scopeType === 'CLASS' ? e.scopeId : '') || '';

    this.scopeId = e.scopeType === 'SECTION' ? (e.scopeId || '') : '';

    this.formError.set('');

    this.selectedEvent.set(null);

    this.showForm.set(true);

  }



  openDetail(e: any, event?: Event) {

    event?.stopPropagation();

    this.selectedEvent.set(e);

    this.deleteConfirmId.set(null);

  }



  closeDetail() {

    this.selectedEvent.set(null);

    this.deleteConfirmId.set(null);

  }



  resetForm() {

    this.title = ''; this.eventDate = ''; this.startTime = ''; this.endTime = '';

    this.location = ''; this.scopeType = 'SCHOOL'; this.scopeId = ''; this.selectedClassId = '';

    this.formError.set('');

  }



  onSubmit() {

    this.formError.set('');

    const body: any = { title: this.title, eventDate: this.eventDate, scopeType: this.scopeType };

    if (this.scopeType === 'CLASS' && this.selectedClassId) body.scopeId = this.selectedClassId;

    else if (this.scopeType === 'SECTION' && this.scopeId) body.scopeId = this.scopeId;

    body.startTime = this.startTime || '';

    body.endTime = this.endTime || '';

    body.location = this.location || '';



    const editing = this.editingId();

    const request = editing

      ? this.api.patch(`/api/school/events/${editing}`, body)

      : this.api.post('/api/school/events', body);



    request.subscribe({

      next: () => {

        this.showForm.set(false);

        this.editingId.set(null);

        this.resetForm();

        this.load();

      },

      error: (err) => this.formError.set(err.error?.errors?.[0]?.message || 'Failed to save event')

    });

  }



  confirmDelete(e: any) {

    this.deleteConfirmId.set(e.id);

  }



  cancelDelete() {

    this.deleteConfirmId.set(null);

  }



  deleteEvent(id: string) {

    this.api.delete(`/api/school/events/${id}`).subscribe({

      next: () => {

        this.closeDetail();

        this.load();

      },

      error: (err) => this.formError.set(err.error?.errors?.[0]?.message || 'Failed to delete event')

    });

  }

}
