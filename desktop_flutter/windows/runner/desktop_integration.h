#pragma once

#include <flutter/binary_messenger.h>
#include <windows.h>

#include <memory>

class DesktopIntegration {
 public:
  DesktopIntegration(flutter::BinaryMessenger* messenger, HWND window);
  ~DesktopIntegration();
  bool HandleMessage(UINT message, WPARAM wparam, LPARAM lparam);

 private:
  struct State;
  std::unique_ptr<State> state_;
};
