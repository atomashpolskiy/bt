# UPGRADE INSTRUCTIONS

## 1.5

* `bt.BaseClientBuilder#runtime(BtRuntime)` is now protected instead of public. Use builder method `bt.Bt#client(BtRuntime)` to attach a newly created client to a shared runtime.
