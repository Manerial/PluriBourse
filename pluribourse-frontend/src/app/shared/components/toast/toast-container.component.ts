import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { ToastService } from './toast.service';

@Component({
  selector: 'app-toast-container',
  standalone: true,
  imports: [TranslatePipe, RouterLink],
  templateUrl: './toast-container.component.html',
  styleUrl: './toast-container.component.scss',
})
export class ToastContainerComponent {
  protected readonly toastService = inject(ToastService);

  // A ctrl/cmd/middle click opens the link in a new tab (native <a> behavior, left untouched
  // here) — closing the toast on the current tab in that case would hide the error notice the
  // admin still needs to see there.
  protected closeUnlessNewTab(event: MouseEvent): void {
    if (event.ctrlKey || event.metaKey || event.shiftKey || event.button === 1) {
      return;
    }
    this.toastService.close();
  }
}
