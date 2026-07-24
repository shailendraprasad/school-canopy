import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-support',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './support.component.html',
  styleUrl: './support.component.css'
})
export class SupportComponent implements OnInit {
  tickets = signal<any[]>([]);
  showCreate = signal(false);
  selectedTicket = signal<any>(null);

  // Create form
  subject = '';
  description = '';
  priority = 'MEDIUM';
  createError = signal('');

  // Comment
  newComment = '';

  constructor(private api: ApiService) {}

  ngOnInit() { this.loadTickets(); }

  loadTickets() {
    this.api.get<any[]>('/api/school/support-tickets').subscribe(res => this.tickets.set(res.data || []));
  }

  createTicket() {
    this.createError.set('');
    if (!this.subject.trim()) { this.createError.set('Subject is required'); return; }
    if (!this.description.trim()) { this.createError.set('Description is required'); return; }

    this.api.post('/api/school/support-tickets', {
      subject: this.subject, description: this.description, priority: this.priority
    }).subscribe({
      next: () => { this.showCreate.set(false); this.subject = ''; this.description = ''; this.priority = 'MEDIUM'; this.loadTickets(); },
      error: (err) => this.createError.set(err.error?.errors?.[0]?.message || 'Failed')
    });
  }

  openTicket(ticket: any) {
    this.api.get<any>(`/api/school/support-tickets/${ticket.id}`).subscribe(res => this.selectedTicket.set(res.data));
  }

  closeDetail() { this.selectedTicket.set(null); }

  addComment() {
    if (!this.newComment.trim()) return;
    const tid = this.selectedTicket()?.id;
    this.api.post(`/api/school/support-tickets/${tid}/comments`, { body: this.newComment }).subscribe({
      next: () => { this.newComment = ''; this.openTicket({ id: tid }); this.loadTickets(); },
      error: () => {}
    });
  }

  updateStatus(status: string) {
    const tid = this.selectedTicket()?.id;
    this.api.patch(`/api/school/support-tickets/${tid}/status`, { status }).subscribe({
      next: () => { this.openTicket({ id: tid }); this.loadTickets(); },
      error: () => {}
    });
  }

  getPriorityColor(p: string): string {
    if (p === 'URGENT') return '#b0473f';
    if (p === 'HIGH') return '#ba7517';
    if (p === 'LOW') return '#888';
    return '#4a6b8a';
  }

  getStatusColor(s: string): string {
    if (s === 'OPEN') return '#ba7517';
    if (s === 'IN_PROGRESS') return '#4a6b8a';
    if (s === 'RESOLVED') return '#2d6a4f';
    return '#888';
  }
}
