package bt.example.cli;

import com.nhl.bootique.cli.Cli;
import com.nhl.bootique.command.CommandMetadata;
import com.nhl.bootique.command.CommandOutcome;
import com.nhl.bootique.command.CommandWithMetadata;
import joptsimple.OptionException;

import java.util.Arrays;
import java.util.List;

public class CliClientCommand extends CommandWithMetadata {

    public CliClientCommand() {
        super(createMetadata());
    }

    private static CommandMetadata createMetadata() {
        return CommandMetadata.builder(CliClientCommand.class)
				.description("Bt Example: Simple command-line torrent downloader")
				.build();
    }

    @Override
    public CommandOutcome run(Cli cli) {

        List<String> argsList = cli.standaloneArguments();
        String[] args = argsList.toArray(new String[argsList.size()]);

        Options options;
        try {
            options = Options.parse(args);
        } catch (OptionException e) {
            Options.printHelp(System.out);
            return CommandOutcome.failed(2, "Illegal arguments: " + Arrays.toString(args));
        }

        new CliClient().runWithOptions(options);
        return CommandOutcome.succeeded();
    }
}
