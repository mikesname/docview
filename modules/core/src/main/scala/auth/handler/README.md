Authentication Handler derived from Play2-Auth
==============================================

This code in the package was largely adapted from the Apache2-licensed Play2-Auth project
(https://github.com/t2v/play2-auth) and modified in the following ways:

 - moved from static to injected components (static components such as the Crypto handlers were
   removed from Play 2.6
 - authorization-related functionality was removed
 - support for generically-typed (non-string) tokens and IDs was removed

