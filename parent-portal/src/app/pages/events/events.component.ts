import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-events',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './events.component.html',
  styleUrl: './events.component.css'
})
export class EventsComponent implements OnInit {
  events = signal<any[]>([]);
  currentYear = new Date().getFullYear();
  currentMonth = new Date().getMonth();
  selectedDate = signal<string>('');

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.api.get<any[]>('/api/parent/events').subscribe(res => this.events.set(res.data || []));
  }

  get monthName(): string {
    return new Date(this.currentYear, this.currentMonth).toLocaleString('default', { month: 'long', year: 'numeric' });
  }

  get calendarDays(): any[] {
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

  prevMonth() { if (this.currentMonth === 0) { this.currentMonth = 11; this.currentYear--; } else { this.currentMonth--; } }
  nextMonth() { if (this.currentMonth === 11) { this.currentMonth = 0; this.currentYear++; } else { this.currentMonth++; } }
  selectDay(day: any) { if (day.inMonth) this.selectedDate.set(day.dateStr); }

  get selectedDateEvents(): any[] {
    if (!this.selectedDate()) return [];
    return this.events().filter(e => e.eventDate === this.selectedDate());
  }

  get upcomingEvents(): any[] {
    const today = new Date().toISOString().split('T')[0];
    return this.events().filter(e => e.eventDate >= today).slice(0, 8);
  }

  formatSelectedDate(): string {
    if (!this.selectedDate()) return '';
    const d = new Date(this.selectedDate() + 'T00:00:00');
    return d.toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' });
  }
}
