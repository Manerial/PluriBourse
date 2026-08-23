import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'app-printers-page',
  standalone: true,
  imports: [MatTabsModule, TranslatePipe, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './printers-page.component.html',
  styleUrl: './printers-page.component.scss',
})
export class PrintersPageComponent {
}
