import { Component, input } from '@angular/core';

@Component({
  selector: 'app-notification-inline',
  standalone: true,
  imports: [],
  templateUrl: './notification-inline.component.html',
  styleUrl: './notification-inline.component.scss',
})
export class NotificationInlineComponent {
  readonly message = input.required<string>();
  readonly variant = input<'warning' | 'error'>('warning');
}
