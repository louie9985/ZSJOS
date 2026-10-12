package maintenance;

import com.sun.tools.attach.VirtualMachine;
import java.nio.file.Path;

public final class AppealMaintenanceAttach {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("PID, reviewed agent jar and config required");
        VirtualMachine machine = VirtualMachine.attach(args[0]);
        try { machine.loadAgent(Path.of(args[1]).toAbsolutePath().toString(), Path.of(args[2]).toAbsolutePath().toString()); }
        finally { machine.detach(); }
    }
}
