#pragma once

#include <flutter_linux/flutter_linux.h>
#include <gtk/gtk.h>

struct DesktopIntegration;
DesktopIntegration* desktop_integration_new(FlView* view, GtkWindow* window);
void desktop_integration_free(DesktopIntegration* integration);
